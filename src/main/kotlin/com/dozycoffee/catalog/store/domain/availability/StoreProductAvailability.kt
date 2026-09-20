package com.dozycoffee.catalog.store.domain.availability

import com.dozycoffee.catalog.core.AggregateRoot
import com.dozycoffee.catalog.store.domain.availability.exception.InventoryEventNotApplicableException
import com.dozycoffee.catalog.store.domain.availability.exception.StockStatusNotManuallyEditableException
import java.time.Instant

// 매장별 상품 판매 가능 여부(판매중/품절). 점주의 진열 의도(StoreDisplaySetting)와 주인·수명이
// 달라 분리했다 — 특히 INVENTORY 출처는 재고관리 서비스가 주인이라 판매 범위에서 빠져도
// 지우지 않는다(요구사항 1.5, 2.4). row가 없으면 출처별 기본값(AvailabilitySource)으로 판단한다.
class StoreProductAvailability internal constructor(
    id: StoreProductAvailabilityId,
    val source: AvailabilitySource,
    stockStatus: StockStatus,
    lastEventAt: Instant? = null,
) : AggregateRoot<StoreProductAvailabilityId>(id) {
    var stockStatus: StockStatus = stockStatus
        private set

    // 마지막으로 반영한 재고 이벤트의 발생 시각. INVENTORY 출처에서만 쓴다.
    var lastEventAt: Instant? = lastEventAt
        private set

    // 점주의 수동 품절 설정/해제(재료 소진 등). 재고 추적 상품은 재고관리 서비스 이벤트로만
    // 바뀌므로 거부한다(요구사항 2.5).
    fun changeByOwner(newStockStatus: StockStatus) {
        if (source != AvailabilitySource.OWNER) {
            throw StockStatusNotManuallyEditableException(id.storeId, id.productId)
        }
        stockStatus = newStockStatus
    }

    // 재고관리 서비스의 재고 생김/없음 이벤트를 반영한다. 이미 반영한 것보다 오래되었거나 같은
    // 시각의 이벤트는 중복 수신·순서 역전으로 보고 무시하며, 반영했는지를 돌려준다.
    fun applyInventoryEvent(
        newStockStatus: StockStatus,
        occurredAt: Instant,
    ): Boolean {
        if (source != AvailabilitySource.INVENTORY) {
            throw InventoryEventNotApplicableException(id.storeId, id.productId)
        }
        val last = lastEventAt
        if (last != null && !occurredAt.isAfter(last)) {
            return false
        }
        stockStatus = newStockStatus
        lastEventAt = occurredAt
        return true
    }

    companion object {
        // 처음 변경이 일어날 때 만든다(점주의 첫 수동 품절, 또는 첫 재고 이벤트).
        // 복합 식별자라 저장 전에 ID가 정해지므로 NewXxx 단계 없이 바로 만든다.
        fun initial(
            id: StoreProductAvailabilityId,
            source: AvailabilitySource,
        ): StoreProductAvailability = StoreProductAvailability(id, source, source.defaultStockStatus)
    }
}
