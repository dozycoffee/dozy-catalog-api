package com.dozycoffee.catalog.store.domain.display

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.display.exception.DuplicateDisplayOrderProductException

// 점주가 완성한 매장의 최종 진열 순서 전체(요구사항 2.3). 목록의 상품은 그 순서대로 1부터 번호를 받고,
// 목록에 없는 이 매장의 상품은 순서를 정하지 않은 상품이 된다. 빈 목록이면 모든 상품의 순서를 비운다.
class StoreDisplayOrder(
    val storeId: StoreId,
    val productIds: List<ProductId>,
) {
    init {
        val seen = HashSet<ProductId>()
        productIds.firstOrNull { !seen.add(it) }?.let { throw DuplicateDisplayOrderProductException(storeId, it) }
    }

    // (상품, 진열 순서) 목록. 진열 순서는 1부터 시작한다.
    val numbered: List<Pair<ProductId, Int>>
        get() = productIds.mapIndexed { index, productId -> productId to index + 1 }
}
