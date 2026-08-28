package com.dozycoffee.catalog.domain.product.model

sealed class StoreScope {
    data object All : StoreScope()

    data class Limited(
        val targetStoreIds: Set<StoreId>,
    ) : StoreScope()
}
