package com.dozycoffee.catalog.domain.storeproductlisting.model

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.shared.AggregateRoot
import com.dozycoffee.catalog.domain.storeproductlisting.exception.StockStatusNotManuallyEditableException

// 매장별 진열/노출 설정. row의 부재 자체가 "기본값(노출, 판매중)으로 취급 중"을
// 의미하므로(Lazy 생성), 이 애그리거트는 점주가 무언가를 한 번이라도 커스터마이징한
// 매장에만 존재한다.
class StoreProductListing internal constructor(
    id: StoreProductListingId,
    val storeId: StoreId,
    val productId: ProductId,
    displayOrder: Int? = null,
    visibility: Visibility = Visibility.VISIBLE,
    stockStatus: StockStatus = StockStatus.ON_SALE,
) : AggregateRoot<StoreProductListingId>(id) {
    var displayOrder: Int? = displayOrder
        private set
    var visibility: Visibility = visibility
        private set
    var stockStatus: StockStatus = stockStatus
        private set

    fun changeDisplayOrder(newDisplayOrder: Int?) {
        this.displayOrder = newDisplayOrder
    }

    fun show() {
        this.visibility = Visibility.VISIBLE
    }

    fun hide() {
        this.visibility = Visibility.HIDDEN
    }

    // 점주의 수동 품절 토글(재료 소진 등). 재고 추적 상품의 품절은 재고관리
    // 서비스 이벤트로만 전환되므로 점주 요청을 거부한다.
    // tracksInventory는 Product가 소유한 값이라 이 애그리거트가 알 수 없어
    // 호출자(application)가 조회해 전달한다.
    fun changeStockStatusByOwner(
        newStockStatus: StockStatus,
        tracksInventory: Boolean,
    ) {
        if (tracksInventory) {
            throw StockStatusNotManuallyEditableException(storeId, productId)
        }
        this.stockStatus = newStockStatus
    }

    // 재고관리 서비스의 재고 소진/재입고 이벤트를 반영한다. 점주 경로와 달리
    // 검증 없이 그대로 따르는 이유는 재고 수량의 source of truth가 그쪽이기 때문.
    fun applyInventoryStockStatus(newStockStatus: StockStatus) {
        this.stockStatus = newStockStatus
    }
}
