package com.dozycoffee.catalog.store.presentation.dto

import com.dozycoffee.catalog.common.web.InvalidRequestException
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.application.availability.command.ChangeStockStatusByOwnerCommand
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.application.display.command.ReplaceDisplayOrderCommand
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.display.Visibility

// 진열 순서 일괄 변경(요구사항 2.3). productIds는 점주가 완성한 최종 순서 전체다.
// Jackson은 목록 안의 null을 막지 않으므로 원소를 nullable로 받아 여기서 요청 오류로 거른다.
data class ReplaceDisplayOrderRequest(
    val productIds: List<Long?>,
) {
    fun toCommand(storeId: StoreId): ReplaceDisplayOrderCommand {
        val ids =
            productIds.mapIndexed { index, id ->
                id ?: throw InvalidRequestException("요청 본문의 'productIds[$index]' 값이 없습니다")
            }
        return ReplaceDisplayOrderCommand(storeId, ids.map(::ProductId))
    }
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
