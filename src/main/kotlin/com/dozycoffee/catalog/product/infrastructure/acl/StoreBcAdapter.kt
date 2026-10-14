package com.dozycoffee.catalog.product.infrastructure.acl

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.port.StoreAssignabilityPort
import com.dozycoffee.catalog.product.application.port.UnassignableReason
import com.dozycoffee.catalog.product.domain.product.exception.StoreServiceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

// Store 서비스의 `POST /api/v1/internal/stores/assignability-check`로 판매 범위의 대상 매장을 확인한다(요구사항 1.5, #137).
// 한 번에 1~1,000개만 받으므로 1,000개씩 나눠 순서대로 부른다. 호출은 system token(catalog의 system client,
// dozy.auth.client.*)으로 하고, 토큰 발급·캐시는 dozy-auth 스타터가 맡는다.
// 확인하지 못한 채 통과시키면 없는 매장이 판매 범위에 들어가므로, 어떤 실패든 StoreServiceUnavailableException으로 요청을 거부한다.
// 재시도하지 않는다 — 사람이 누른 요청이라 실패를 바로 알리는 편이 낫다.
@Component
@EnableConfigurationProperties(CatalogStoreProperties::class)
@ConditionalOnExpression("!'\${catalog.store.base-url:}'.isBlank()")
class StoreBcAdapter(
    @Qualifier("dozySystemWebClient") webClientBuilder: WebClient.Builder,
    private val properties: CatalogStoreProperties,
) : StoreAssignabilityPort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: WebClient = webClientBuilder.baseUrl(requireNotNull(properties.baseUrl)).build()

    override suspend fun findUnassignable(storeIds: Set<StoreId>): Map<StoreId, UnassignableReason> {
        if (storeIds.isEmpty()) return emptyMap()
        val result = LinkedHashMap<StoreId, UnassignableReason>()
        storeIds.sortedBy { it.value }.chunked(MAX_STORE_IDS).forEach { chunk -> result += check(chunk) }
        return result
    }

    private suspend fun check(chunk: List<StoreId>): Map<StoreId, UnassignableReason> {
        val response =
            try {
                client
                    .post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(AssignabilityCheckRequest(chunk.map { it.value }))
                    .retrieve()
                    .bodyToMono(AssignabilityCheckResponse::class.java)
                    .timeout(properties.timeout)
                    .awaitSingle()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Store 서비스에 매장을 확인하지 못했습니다: {}", e.toString())
                throw StoreServiceUnavailableException(e)
            }
        return response.unassignableStores.associate { entry ->
            val reason =
                runCatching { UnassignableReason.valueOf(entry.reason) }.getOrElse {
                    // 계약에 없는 사유를 지정 가능으로 보면 안 된다. 계약이 어긋난 것이므로 확인하지 못한 것으로 거부한다.
                    log.error("Store 서비스가 알 수 없는 사유를 돌려줬습니다: {}", entry.reason)
                    throw StoreServiceUnavailableException(IllegalStateException("알 수 없는 사유: ${entry.reason}"))
                }
            StoreId(entry.storeId) to reason
        }
    }

    private data class AssignabilityCheckRequest(
        val storeIds: List<Long>,
    )

    private data class AssignabilityCheckResponse(
        val unassignableStores: List<UnassignableStore> = emptyList(),
    )

    private data class UnassignableStore(
        val storeId: Long,
        val reason: String,
    )

    private companion object {
        const val PATH = "/api/v1/internal/stores/assignability-check"
        const val MAX_STORE_IDS = 1000
    }
}
