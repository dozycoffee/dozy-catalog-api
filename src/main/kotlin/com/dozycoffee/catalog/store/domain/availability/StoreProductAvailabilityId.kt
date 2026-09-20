package com.dozycoffee.catalog.store.domain.availability

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.domain.product.model.ProductId

// store_product_availabilities의 복합 PK(store_id, product_id)를 그대로 식별자로 쓴다.
data class StoreProductAvailabilityId(
    val storeId: StoreId,
    val productId: ProductId,
)
