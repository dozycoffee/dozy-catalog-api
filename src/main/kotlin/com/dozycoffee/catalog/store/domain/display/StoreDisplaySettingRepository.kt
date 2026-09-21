package com.dozycoffee.catalog.store.domain.display

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySetting
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingId

interface StoreDisplaySettingRepository {
    suspend fun findById(id: StoreDisplaySettingId): StoreDisplaySetting?

    // 노출 판단 시 진열 설정 조회. null은 "설정 없음 = 기본값으로 노출 중"을 의미하므로
    // 호출자는 이를 오류로 다루지 않는다.
    suspend fun findByStoreAndProduct(
        storeId: StoreId,
        productId: ProductId,
    ): StoreDisplaySetting?

    // 점주의 매장 진열 목록 조회.
    suspend fun findAllByStore(storeId: StoreId): List<StoreDisplaySetting>

    // 판매 범위 변경 시 제외 대상을 가려내기 위해 이 상품의 모든 진열 설정을 읽는다.
    suspend fun findAllByProduct(productId: ProductId): List<StoreDisplaySetting>

    // row가 없으면 만들고 있으면 그대로 돌려주는 유일한 생성 경로. 점주가 노출·진열 순서를
    // 처음 바꾸는 시점에(Lazy) row가 생긴다. 동시 요청에도 중복 row가 생기지 않도록
    // INSERT ... ON CONFLICT (store_id, product_id) DO UPDATE 원자적 upsert로 구현한다.
    suspend fun findOrCreate(
        storeId: StoreId,
        productId: ProductId,
    ): StoreDisplaySetting

    // 진열 설정은 낙관적 잠금 없이 점주의 최신 의도가 이긴다(docs/adr/0013). 전체 행을 덮어쓰면 노출 변경과
    // 진열 순서 변경이 동시에 들어올 때 한쪽이 다른 쪽을 옛 값으로 되돌리므로, 바꾼 필드만 저장한다.
    suspend fun saveVisibility(setting: StoreDisplaySetting)

    // 이 매장의 진열 순서 일괄 변경을 트랜잭션이 끝날 때까지 한 번에 하나만 하도록 매장 단위로 잠근다.
    // 같은 매장의 두 일괄 변경이 섞이지 않게 하려는 것이라 진열 순서를 바꾸기 전에 먼저 부른다.
    suspend fun lockStoreDisplayOrder(storeId: StoreId)

    // 매장의 진열 순서를 order로 통째로 바꾼다. 목록의 상품은 번호를 저장하고(설정이 없으면 만든다),
    // 이 매장의 다른 설정은 진열 순서를 비운다. 노출 여부는 바꾸지 않는다.
    suspend fun replaceDisplayOrder(order: StoreDisplayOrder)

    // 판매 범위에서 제외된 매장의 진열 설정을 삭제한다(요구사항 1.5) — 해당 매장이 다시
    // 대상에 포함되어도 복원하지 않고 기본값으로 새로 시작한다.
    suspend fun deleteAll(ids: Collection<StoreDisplaySettingId>)
}
