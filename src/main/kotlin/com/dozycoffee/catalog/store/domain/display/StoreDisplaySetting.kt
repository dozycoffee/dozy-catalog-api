package com.dozycoffee.catalog.store.domain.display

import com.dozycoffee.catalog.core.AggregateRoot
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId

// 점주가 본사 카탈로그 위에 얹는 매장별 진열 설정(노출·숨김, 진열 순서). row의 부재 자체가
// "기본값(노출)으로 취급 중"을 의미하므로(Lazy 생성), 점주가 한 번이라도 커스터마이징한
// 매장·상품에만 존재한다. 품절 여부는 주인과 수명이 달라 StoreProductAvailability에 둔다.
class StoreDisplaySetting internal constructor(
    id: StoreDisplaySettingId,
    val storeId: StoreId,
    val productId: ProductId,
    displayOrder: Int? = null,
    visibility: Visibility = Visibility.VISIBLE,
) : AggregateRoot<StoreDisplaySettingId>(id) {
    var displayOrder: Int? = displayOrder
        private set
    var visibility: Visibility = visibility
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
}
