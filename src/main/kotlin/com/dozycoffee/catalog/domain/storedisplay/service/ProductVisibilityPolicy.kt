package com.dozycoffee.catalog.domain.storedisplay.service

import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.shared.StoreId
import com.dozycoffee.catalog.domain.storeavailability.AvailabilitySource
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailability
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySetting
import com.dozycoffee.catalog.domain.storedisplay.model.StoreVisibility
import com.dozycoffee.catalog.domain.storedisplay.model.Visibility

// 요구사항 3장의 매장별 노출 판단 로직. 어느 단계든 조건에 맞지 않으면 그 즉시
// 비노출로 종료한다. Product, StoreDisplaySetting, StoreProductAvailability를 함께 봐야 하는
// 판단이라 어느 한 애그리거트에 두지 않고 도메인 서비스로 분리했다.
object ProductVisibilityPolicy {
    // displaySetting이 null이면 해당 매장에 진열 설정이 없다는 뜻 — 기본값(노출)으로 간주한다.
    // availability가 null이면 판매 가능 여부가 바뀐 적이 없다는 뜻 — 출처별 기본값을 쓴다
    // (재고 추적 상품은 처음 재고 0이라 품절, 재고 미추적 상품은 판매중).
    fun resolve(
        product: Product,
        storeId: StoreId,
        displaySetting: StoreDisplaySetting?,
        availability: StoreProductAvailability?,
    ): StoreVisibility {
        if (product.status != ProductStatus.ACTIVE) {
            return StoreVisibility.NotVisible
        }
        if (!product.storeScope.covers(storeId)) {
            return StoreVisibility.NotVisible
        }
        if (displaySetting?.visibility == Visibility.HIDDEN) {
            return StoreVisibility.NotVisible
        }
        val stockStatus =
            availability?.stockStatus
                ?: AvailabilitySource.of(product.tracksInventory).defaultStockStatus
        return StoreVisibility.Visible(stockStatus)
    }
}
