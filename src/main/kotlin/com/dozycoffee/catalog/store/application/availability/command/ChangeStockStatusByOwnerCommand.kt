package com.dozycoffee.catalog.store.application.availability.command

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.availability.StockStatus

// 점주의 수동 품절 설정·해제 입력(요구사항 2.5). 재고 추적 상품이면 도메인이 거부한다.
data class ChangeStockStatusByOwnerCommand(
    val storeId: StoreId,
    val productId: ProductId,
    val stockStatus: StockStatus,
)
