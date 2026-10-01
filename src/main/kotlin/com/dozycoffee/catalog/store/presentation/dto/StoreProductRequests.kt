package com.dozycoffee.catalog.store.presentation.dto

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.application.availability.command.ChangeStockStatusByOwnerCommand
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.application.display.command.ReplaceDisplayOrderCommand
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.display.Visibility

// 진열 순서 일괄 변경(요구사항 2.3). productIds는 점주가 완성한 최종 순서 전체다.
data class ReplaceDisplayOrderRequest(
    val productIds: List<Long>,
) {
    fun toCommand(storeId: StoreId) = ReplaceDisplayOrderCommand(storeId, productIds.map(::ProductId))
}

// 숨김·노출 전환(요구사항 2.2).
data class ChangeVisibilityRequest(
    val visibility: Visibility,
) {
    fun toCommand(
        storeId: StoreId,
        productId: ProductId,
    ) = ChangeVisibilityCommand(storeId, productId, visibility)
}

// 수동 품절 설정·해제(요구사항 2.5). 재고 추적 상품이면 도메인이 거부한다.
data class ChangeStockStatusRequest(
    val stockStatus: StockStatus,
) {
    fun toCommand(
        storeId: StoreId,
        productId: ProductId,
    ) = ChangeStockStatusByOwnerCommand(storeId, productId, stockStatus)
}
