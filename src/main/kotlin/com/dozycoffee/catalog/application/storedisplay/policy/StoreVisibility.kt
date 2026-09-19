package com.dozycoffee.catalog.application.storedisplay.policy

import com.dozycoffee.catalog.domain.storeavailability.StockStatus

// 매장별 노출 판단의 결과. "비노출"과 "노출되지만 품절"은 전혀 다른 상태인데
// Boolean 하나로는 구분할 수 없어 타입으로 분리했다 — 품절이어도 점주가 숨기지
// 않았다면 노출되며, 구매 불가로만 표시된다.
sealed class StoreVisibility {
    data object NotVisible : StoreVisibility()

    data class Visible(
        val stockStatus: StockStatus,
    ) : StoreVisibility()
}
