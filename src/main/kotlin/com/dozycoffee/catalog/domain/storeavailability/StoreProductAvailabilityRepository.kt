package com.dozycoffee.catalog.domain.storeavailability

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId

interface StoreProductAvailabilityRepository {
    // null은 "아직 변경된 적 없음 = 출처별 기본값"을 의미하므로 호출자는 오류로 다루지 않는다.
    suspend fun findById(id: StoreProductAvailabilityId): StoreProductAvailability?

    // 점주의 매장 상품 목록 조회용.
    suspend fun findAllByStore(storeId: StoreId): List<StoreProductAvailability>

    // 판매 범위 변경 시 제외된 매장의 OWNER 출처 row를 가려내기 위해 읽는다.
    suspend fun findAllByProduct(productId: ProductId): List<StoreProductAvailability>

    // INVENTORY 출처는 동시에 도착한 이벤트끼리 오래된 값이 덮어쓰지 않도록
    // last_event_at을 비교하는 조건부 upsert로 구현한다(docs/erd.md 동시성 처리).
    suspend fun save(availability: StoreProductAvailability): StoreProductAvailability

    // 판매 범위에서 제외된 매장의 OWNER 출처 row를 초기화한다(요구사항 1.5).
    // INVENTORY 출처 row는 호출자가 넘기지 않는다.
    suspend fun deleteAll(ids: Collection<StoreProductAvailabilityId>)
}
