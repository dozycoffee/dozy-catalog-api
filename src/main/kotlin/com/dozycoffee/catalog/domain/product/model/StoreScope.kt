package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.domain.shared.StoreId

sealed class StoreScope {
    // 이 판매 범위가 해당 매장을 취급 대상으로 포함하는지. Limited의 대상 목록은
    // 비어 있을 수 있고(1.5), 그 경우 어떤 매장도 포함하지 않는다.
    abstract fun covers(storeId: StoreId): Boolean

    data object All : StoreScope() {
        override fun covers(storeId: StoreId): Boolean = true
    }

    data class Limited(
        val targetStoreIds: Set<StoreId>,
    ) : StoreScope() {
        override fun covers(storeId: StoreId): Boolean = storeId in targetStoreIds
    }
}
