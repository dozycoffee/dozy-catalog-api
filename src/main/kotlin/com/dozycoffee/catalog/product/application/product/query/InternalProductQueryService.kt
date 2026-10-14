package com.dozycoffee.catalog.product.application.product.query

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionConfig
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionResolver
import com.dozycoffee.catalog.product.application.port.ProductDisplayInfo
import com.dozycoffee.catalog.product.application.port.ProductDisplayQueryPort
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.tag.Tag
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import org.springframework.stereotype.Service

// Store 서비스가 부르는 상품 조회(docs/api/store-service.md, ADR-0020). 매장과 점주를 모르고 상품 자체만 본다.
// 판매 가능 여부와 노출은 Store가 판단하므로 상품 상태와 판매 범위로 거르지 않고, 판매 범위를 읽지도 않는다.
// 기준 정보와 유효 옵션은 조회 포트(ProductDisplayQueryPort)로 필요한 값만 읽는다(#130). 상품 애그리거트로 복원하면
// 한정 판매 상품의 대상 매장 목록을 쓰지도 않으면서 함께 읽는다. 상태 목록만 애그리거트가 필요하다(판매 범위가 응답 내용이다).
@Service
class InternalProductQueryService(
    private val displayQuery: ProductDisplayQueryPort,
    private val productRepository: ProductRepository,
    private val optionGroupRepository: OptionGroupRepository,
    private val tagRepository: TagRepository,
    private val transactionRunner: TransactionRunner,
) {
    // 상품 기준 정보를 ID로 조회한다. 상태와 무관하고 없는 ID는 빠지며 상품 id 순이다.
    // 표시용 시작가는 목록에서 상품마다 유효 옵션 구성을 따로 부르지 않도록 함께 계산한다.
    suspend fun summaries(ids: Set<ProductId>): List<ProductSummaryView> =
        transactionRunner.inTransaction {
            val infos = displayQuery.findAll(ids)
            val optionGroups = loadOptionGroups(infos)
            val tags = tagRepository.findAll(ids = infos.flatMap { it.tagIds }.toSet()).associateBy { it.id }
            infos.map { info ->
                ProductSummaryView(
                    info = info,
                    displayStartingPrice = effectiveOptionsOf(info, optionGroups).displayStartingPrice,
                    // 태그는 이름 순으로 돌려받은 것을 그대로 쓴다(없어진 태그는 연결도 함께 사라지므로 빠질 일이 없다).
                    tags = tags.values.filter { it.id in info.tagIds },
                )
            }
        }

    // 매장과 상품 상태를 보지 않고 상품만으로 계산한다. 판매 가능 여부는 Store가 먼저 판단한다.
    suspend fun getEffectiveOptions(productId: ProductId): EffectiveOptionConfig =
        transactionRunner.inTransaction {
            val info = displayQuery.findById(productId) ?: throw ProductNotFoundException(productId)
            effectiveOptionsOf(info, loadOptionGroups(listOf(info)))
        }

    // 상품 상태 전체를 id 순으로 한 페이지 준다(docs/events/product.md 6절). 삭제된 상품은 애초에 없다.
    // 다음 페이지가 있으면 nextCursor가 이 페이지의 마지막 상품 ID다.
    suspend fun states(
        after: ProductId?,
        size: Int,
    ): ProductStatePage =
        transactionRunner.inTransaction {
            require(size > 0) { "size는 1 이상이어야 합니다: $size" }
            val fetched = productRepository.findPageAfter(after, size + 1)
            val page = fetched.take(size)
            ProductStatePage(page, nextCursor = if (fetched.size > size) page.last().id else null)
        }

    private suspend fun loadOptionGroups(infos: Collection<ProductDisplayInfo>): Map<OptionGroupId, OptionGroup> {
        val ids = infos.flatMap { info -> info.optionGroupLinks.map { it.id } }.toSet()
        if (ids.isEmpty()) return emptyMap()
        return optionGroupRepository.findAll(ids = ids).associateBy { it.id }
    }

    private fun effectiveOptionsOf(
        info: ProductDisplayInfo,
        optionGroups: Map<OptionGroupId, OptionGroup>,
    ): EffectiveOptionConfig =
        EffectiveOptionResolver.resolve(
            info.id,
            info.basePrice,
            info.optionGroupLinks,
            info.optionGroupLinks.map { link -> optionGroups[link.id] ?: throw OptionGroupNotFoundException(link.id) },
        )
}

data class ProductSummaryView(
    val info: ProductDisplayInfo,
    val displayStartingPrice: Money,
    val tags: List<Tag>,
)

data class ProductStatePage(
    val products: List<Product>,
    val nextCursor: ProductId?,
)
