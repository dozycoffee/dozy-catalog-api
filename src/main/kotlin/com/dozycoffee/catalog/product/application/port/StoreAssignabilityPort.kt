package com.dozycoffee.catalog.product.application.port

import com.dozycoffee.catalog.core.StoreId

// 판매 범위의 대상 매장으로 지정할 수 있는 매장인지 Store 서비스에 확인한다(요구사항 1.5).
// 매장의 원본은 Catalog가 아니라 Store 서비스이므로 외부 시스템 포트로 둔다.
// 개점 준비·영업 중·휴업 매장은 지정할 수 있고, 없는 매장과 폐점한 매장은 지정할 수 없다.
interface StoreAssignabilityPort {
    // 지정할 수 없는 매장과 그 사유만 돌려준다. 비어 있으면 모두 지정할 수 있다.
    // 확인하지 못하면(Store 서비스 장애 등) 통과시키지 않고 StoreServiceUnavailableException을 던진다.
    suspend fun findUnassignable(storeIds: Set<StoreId>): Map<StoreId, UnassignableReason>
}

enum class UnassignableReason {
    // Store 서비스에 없는 매장
    NOT_FOUND,

    // 폐점한 매장
    CLOSED,
}
