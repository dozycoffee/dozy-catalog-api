package com.dozycoffee.catalog.domain.storeproductlisting

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.storeproductlisting.model.StoreProductListing
import com.dozycoffee.catalog.domain.storeproductlisting.model.StoreProductListingId

interface StoreProductListingRepository {
    suspend fun findById(id: StoreProductListingId): StoreProductListing?

    // 노출 판단 시 개별 설정 조회. null은 "설정 없음 = 기본값으로 노출 중"을 의미하므로
    // 호출자는 이를 오류로 다루지 않는다.
    suspend fun findByStoreAndProduct(
        storeId: StoreId,
        productId: ProductId,
    ): StoreProductListing?

    // 점주의 매장 진열 목록 조회.
    suspend fun findAllByStore(storeId: StoreId): List<StoreProductListing>

    // 판매 범위 변경 시 제외 대상을 가려내기 위해 이 상품의 모든 개별 설정을 읽는다.
    suspend fun findAllByProduct(productId: ProductId): List<StoreProductListing>

    // row가 없으면 만들고 있으면 그대로 돌려주는 유일한 생성 경로. 재고 미추적
    // 상품은 점주가 진열/노출을 처음 변경하는 시점에(Lazy), 재고 추적 상품은 입고
    // 이벤트가 도착하는 시점에 이 경로로 row가 생긴다. 동시 요청에도 중복 row가
    // 생기지 않도록 INSERT ... ON CONFLICT (store_id, product_id) DO UPDATE
    // 원자적 upsert로 구현한다.
    suspend fun findOrCreate(
        storeId: StoreId,
        productId: ProductId,
    ): StoreProductListing

    suspend fun save(listing: StoreProductListing): StoreProductListing

    // 판매 범위에서 제외된 매장의 개별 설정을 삭제한다(1.5) — 해당 매장이 다시
    // 대상에 포함되어도 복원하지 않고 기본값으로 새로 시작한다.
    suspend fun deleteAll(ids: Collection<StoreProductListingId>)
}
