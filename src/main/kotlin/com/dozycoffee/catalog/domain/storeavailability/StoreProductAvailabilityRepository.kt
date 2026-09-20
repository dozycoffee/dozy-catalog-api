package com.dozycoffee.catalog.domain.storeavailability

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.domain.product.model.ProductId

interface StoreProductAvailabilityRepository {
    // null은 "아직 변경된 적 없음 = 출처별 기본값"을 의미하므로 호출자는 오류로 다루지 않는다.
    suspend fun findById(id: StoreProductAvailabilityId): StoreProductAvailability?

    // 점주의 매장 상품 목록 조회용.
    suspend fun findAllByStore(storeId: StoreId): List<StoreProductAvailability>

    // 판매 범위 변경 시 제외된 매장의 OWNER 출처 row를 가려내기 위해 읽는다.
    suspend fun findAllByProduct(productId: ProductId): List<StoreProductAvailability>

    // 점주의 수동 품절 설정/해제를 저장한다(OWNER 출처만). row가 없으면 만들고, 있으면 점주의 최신 의도로 덮어쓴다.
    suspend fun saveByOwner(availability: StoreProductAvailability)

    // 재고 이벤트를 반영한 상태를 저장한다(INVENTORY 출처만). 동시에 도착한 이벤트끼리 오래된 값이 덮어쓰지 않도록
    // 저장된 last_event_at보다 새 이벤트일 때만 쓰는 조건부 upsert로 구현한다(docs/erd.md 동시성 처리).
    // 애그리거트가 이미 오래된 이벤트를 걸러도, 그 사이 다른 트랜잭션이 더 새 이벤트를 반영했을 수 있어 DB에서 한 번 더 막는다.
    // 실제로 반영했는지를 돌려준다.
    suspend fun saveInventoryEvent(availability: StoreProductAvailability): Boolean

    // 판매 범위에서 제외된 매장의 OWNER 출처 row를 초기화한다(요구사항 1.5).
    // INVENTORY 출처 row는 호출자가 넘기지 않는다.
    suspend fun deleteAll(ids: Collection<StoreProductAvailabilityId>)
}
