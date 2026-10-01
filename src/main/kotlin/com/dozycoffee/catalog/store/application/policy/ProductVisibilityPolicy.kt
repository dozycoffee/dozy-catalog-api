package com.dozycoffee.catalog.store.application.policy

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.store.domain.availability.AvailabilitySource
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailability
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySetting
import com.dozycoffee.catalog.store.domain.display.Visibility

// 요구사항 3장의 매장별 노출 판단 로직. 어느 단계든 조건에 맞지 않으면 그 즉시
// 비노출로 종료한다. Product, StoreDisplaySetting, StoreProductAvailability를 함께 봐야 하는
// 판단이라 어느 한 애그리거트에 두지 않고 application 정책으로 둔다(ADR-0012). I/O 없이 판단만 한다.
object ProductVisibilityPolicy {
    // displaySetting이 null이면 해당 매장에 진열 설정이 없다는 뜻 — 기본값(노출)으로 간주한다.
    // availability가 null이면 판매 가능 여부가 바뀐 적이 없다는 뜻 — 출처별 기본값을 쓴다
    // (재고 추적 상품은 처음 재고 0이라 품절, 재고 미추적 상품은 판매중).
    fun resolve(
        product: Product,
        storeId: StoreId,
        displaySetting: StoreDisplaySetting?,
        availability: StoreProductAvailability?,
    ): StoreVisibility =
        resolve(
            status = product.status,
            storeScope = product.storeScope,
            tracksInventory = product.tracksInventory,
            storeId = storeId,
            visibility = displaySetting?.visibility,
            stockStatus = availability?.stockStatus,
        )

    // 애그리거트 대신 판단에 쓰이는 값만 받는 경로. 상품 하나가 아니라 매장 전체를 한꺼번에 보는
    // 조회 전용 모듈(exposure)은 애그리거트를 통째로 불러오지 않는데, 그렇다고 같은 판단을 다시 구현하면
    // 두 경로가 어긋난다. 판단은 여기 한 곳에만 둔다.
    // visibility가 null이면 진열 설정이, stockStatus가 null이면 판매 가능 여부가 없다는 뜻이다.
    fun resolve(
        status: ProductStatus,
        storeScope: StoreScope,
        tracksInventory: Boolean,
        storeId: StoreId,
        visibility: Visibility?,
        stockStatus: StockStatus?,
    ): StoreVisibility {
        if (status != ProductStatus.ACTIVE) {
            return StoreVisibility.NotVisible
        }
        if (!storeScope.covers(storeId)) {
            return StoreVisibility.NotVisible
        }
        return resolveByOwnerSetting(tracksInventory, visibility, stockStatus)
    }

    // 3·4단계만 본다. 상품 상태와 판매 범위는 보지 않고 이 매장의 설정(진열 설정, 판매 가능 여부)만으로 정한 노출 상태다.
    // 점주가 설정을 바꾼 결과를 보여 줄 때 쓴다. 점주는 Active가 아닌 상품의 설정도 미리 바꿀 수 있어(요구사항 2.2)
    // 1단계를 적용하면 바꾼 설정과 무관하게 언제나 비노출로 나오기 때문이다.
    // 이 결과의 비노출은 곧 점주가 숨긴 것이다.
    fun resolveByOwnerSetting(
        tracksInventory: Boolean,
        visibility: Visibility?,
        stockStatus: StockStatus?,
    ): StoreVisibility {
        if (visibility == Visibility.HIDDEN) {
            return StoreVisibility.NotVisible
        }
        return StoreVisibility.Visible(stockStatus ?: AvailabilitySource.of(tracksInventory).defaultStockStatus)
    }
}
