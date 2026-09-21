package com.dozycoffee.catalog.exposure.application.port

import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.exposure.application.ProductExposureFilter
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.Sku
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.display.Visibility

// 노출 판단(요구사항 3장)에 필요한 사실을 여러 테이블에서 한 번에 읽어 오는 조회 포트.
// 애그리거트를 통째로 불러오지 않으므로 Repository를 조합하지 않고 infrastructure가 테이블을 직접 조회한다(ADR-0012).
// 전체 매장 수는 Catalog가 모르는 Store BC의 값이라 여기 담기지 않는다(StoreDirectoryPort).
interface ProductExposureQueryPort {
    // 조건에 맞는 상품을 id 오름차순(등록 순)으로 페이지에 담는다. 매장 설정은 페이지에 든 상품 것만 읽는다.
    suspend fun findPage(
        filter: ProductExposureFilter,
        pageRequest: PageRequest,
    ): Page<ProductExposureRecord>

    suspend fun find(productId: ProductId): ProductExposureRecord?
}

// 상품 하나의 노출 현황을 계산하는 데 필요한 재료.
// storeSettings에는 기본값에서 벗어난 매장만 담긴다. 없는 매장은 기본값(노출, 출처별 판매 가능 여부)이다.
data class ProductExposureRecord(
    val productId: ProductId,
    val sku: Sku?,
    val name: String,
    val status: ProductStatus,
    val storeScope: StoreScope,
    val tracksInventory: Boolean,
    val storeSettings: List<StoreSettingRecord>,
)

// 한 매장이 이 상품에 대해 가진 값. 진열 설정과 판매 가능 여부는 서로 독립이라 한쪽만 있을 수 있고,
// 그 경우 없는 쪽은 null(기본값)이다.
data class StoreSettingRecord(
    val storeId: StoreId,
    val visibility: Visibility?,
    val stockStatus: StockStatus?,
)
