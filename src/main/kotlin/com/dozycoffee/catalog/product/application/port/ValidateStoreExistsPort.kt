package com.dozycoffee.catalog.product.application.port

import com.dozycoffee.catalog.core.StoreId

// 판매 범위의 대상 매장이 실제로 존재하는 매장인지 Store BC에 확인한다(요구사항 1.5).
// 매장의 원본은 Catalog가 아니라 Store BC이므로 외부 시스템 포트로 둔다.
interface ValidateStoreExistsPort {
    // 존재하지 않는 매장 ID만 돌려준다. 비어 있으면 모두 존재하는 것이다.
    // 없는 매장을 알려 주는 쪽이 "전부 존재하는가"보다 거부 메시지를 만들기 좋다.
    suspend fun findMissing(storeIds: Set<StoreId>): Set<StoreId>
}
