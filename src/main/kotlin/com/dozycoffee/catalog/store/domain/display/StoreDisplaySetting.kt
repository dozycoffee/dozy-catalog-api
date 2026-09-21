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
    // 진열 순서는 매장 전체의 순서를 한 번에 바꾸는 StoreDisplayOrder로만 바뀌므로 설정 하나에는 바꾸는 메서드를 두지 않는다.
    val displayOrder: Int? = displayOrder
    var visibility: Visibility = visibility
        private set

    fun show() {
        this.visibility = Visibility.VISIBLE
    }

    fun hide() {
        this.visibility = Visibility.HIDDEN
    }
}
