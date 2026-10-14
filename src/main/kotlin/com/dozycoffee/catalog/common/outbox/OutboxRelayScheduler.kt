package com.dozycoffee.catalog.common.outbox

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

// 아웃박스 릴레이를 주기적으로 부른다(docs/adr/0021). 간격은 앞 실행이 끝난 뒤부터 잰다.
// 인스턴스마다 켜 둬도 된다. 한 번에 한 인스턴스만 보내고 나머지는 건너뛴다. 통합 테스트에서는 끈다(IntegrationTest).
// Kafka에 닿지 못해도 앱은 뜨고 이벤트는 아웃박스에 쌓인다. 쌓인 양은 지표(dozy.catalog.outbox.*)로 본다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "catalog.outbox.relay", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler(
    private val relay: OutboxRelay,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${catalog.outbox.relay.fixed-delay:PT1S}")
    suspend fun relay() {
        try {
            val result = relay.relayOnce()
            if (result.published + result.failed > 0) {
                log.info("아웃박스 릴레이: 전송 {}건, 실패 {}건(격리 {}건)", result.published, result.failed, result.quarantined)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // DB 장애처럼 대상 조회부터 실패한 경우다. 이벤트는 아웃박스에 남아 다음 실행에서 다시 보낸다.
            log.error("아웃박스 릴레이를 실행하지 못했습니다. 다음 실행에서 다시 시도합니다", e)
        }
    }

    @Scheduled(fixedDelayString = "\${catalog.outbox.cleanup.fixed-delay:PT1H}")
    suspend fun cleanup() {
        try {
            val deleted = relay.cleanup()
            if (deleted > 0) log.info("아웃박스에서 전송이 끝난 이벤트 {}건을 지웠습니다", deleted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("아웃박스 정리를 실행하지 못했습니다. 다음 실행에서 다시 시도합니다", e)
        }
    }

    @Scheduled(fixedDelayString = "\${catalog.outbox.metrics-refresh-delay:PT15S}")
    suspend fun refreshMetrics() {
        try {
            relay.refreshMetrics()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("아웃박스 지표를 갱신하지 못했습니다", e)
        }
    }
}
