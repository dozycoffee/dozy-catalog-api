package com.dozycoffee.catalog.product.application.product.query

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionConfig
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionResolver
import com.dozycoffee.catalog.product.application.port.ProductSearchCondition
import com.dozycoffee.catalog.product.application.port.ProductSearchOrder
import com.dozycoffee.catalog.product.application.port.ProductSearchQueryPort
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import org.springframework.stereotype.Service

// 가맹점주가 판매하는 상품의 조회(요구사항 1.12, 2.1). 요청자의 매장 ID 집합을 받아, ACTIVE이고 그중 하나라도 판매 범위에 든
// 상품만 돌려준다. 매장이 여럿이면 합집합이고, 매장이 없으면 아무것도 보이지 않는다.
// 범위 밖 상품은 목록에서 빠지고, 단건 조회는 존재 여부를 드러내지 않도록 없는 상품과 똑같이 ProductNotFoundException이다.
// 본사 조회(ProductQueryService, ProductApplicationService.get)와 같은 조회 로직을 쓰고 범위만 다르다.
// 본사 내부 정보(상품 그룹, 판매 범위, 버전)를 빼는 일은 presentation이 응답을 만들 때 한다.
@Service
class SellableProductQueryService(
    private val searchQuery: ProductSearchQueryPort,
    private val productRepository: ProductRepository,
    private val optionGroupRepository: OptionGroupRepository,
    private val transactionRunner: TransactionRunner,
) {
    // 본사 등록 순이다.
    suspend fun search(
        storeIds: Set<StoreId>,
        filter: SellableProductFilter = SellableProductFilter(),
        pageRequest: PageRequest = PageRequest(),
    ): Page<Product> =
        transactionRunner.inTransaction {
            val condition =
                ProductSearchCondition(
                    ids = filter.ids,
                    keyword = filter.keyword,
                    categoryId = filter.categoryId,
                    tagId = filter.tagId,
                    status = ProductStatus.ACTIVE,
                    coveredStoreIds = storeIds,
                    order = ProductSearchOrder.OLDEST_FIRST,
                )
            productRepository.loadPage(searchQuery.search(condition, pageRequest))
        }

    suspend fun get(
        productId: ProductId,
        storeIds: Set<StoreId>,
    ): Product = transactionRunner.inTransaction { requireSellable(productId, storeIds) }

    // 본사 API와 같은 유효 옵션 구성이다(요구사항 1.9). 범위 확인과 계산을 한 트랜잭션에서 한다.
    suspend fun getEffectiveOptions(
        productId: ProductId,
        storeIds: Set<StoreId>,
    ): EffectiveOptionConfig =
        transactionRunner.inTransaction {
            val product = requireSellable(productId, storeIds)
            val optionGroups =
                product.optionGroupLinks.map { link ->
                    optionGroupRepository.findById(link.id) ?: throw OptionGroupNotFoundException(link.id)
                }
            EffectiveOptionResolver.resolve(product, optionGroups)
        }

    // 노출 판단 1·2단계(요구사항 3장)와 같은 기준이다. 목록의 조회 포트 조건(status + coveredStoreIds)과 어긋나면
    // 목록에 있는 상품을 단건으로 못 보게 되므로 함께 고친다.
    private suspend fun requireSellable(
        productId: ProductId,
        storeIds: Set<StoreId>,
    ): Product {
        val product = productRepository.findById(productId)
        if (product == null || product.status != ProductStatus.ACTIVE || storeIds.none { product.storeScope.covers(it) }) {
            throw ProductNotFoundException(productId)
        }
        return product
    }
}
