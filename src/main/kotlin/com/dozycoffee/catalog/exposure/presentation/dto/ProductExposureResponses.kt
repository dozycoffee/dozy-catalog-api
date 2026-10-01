package com.dozycoffee.catalog.exposure.presentation.dto

import com.dozycoffee.catalog.exposure.application.ProductExposureDetail
import com.dozycoffee.catalog.exposure.application.ProductExposureSummary
import com.dozycoffee.catalog.exposure.application.StoreExposure
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.domain.availability.StockStatus

// 노출 현황 요약 한 줄(docs/api/exposure.md의 ProductExposure). 상품을 알아볼 정보는 sku·name·status까지만 담는다.
data class ProductExposureResponse(
    val productId: Long,
    val sku: String?,
    val name: String,
    val status: ProductStatus,
    val sellableStoreCount: Int,
    val exposedStoreCount: Int,
) {
    companion object {
        fun from(summary: ProductExposureSummary) =
            ProductExposureResponse(
                productId = summary.productId.value,
                sku = summary.sku?.value,
                name = summary.name,
                status = summary.status,
                sellableStoreCount = summary.sellableStoreCount,
                exposedStoreCount = summary.exposedStoreCount,
            )
    }
}

// 한 상품의 매장별 노출 상태. 요약과 같은 계산 결과에서 만들므로 수치가 요약과 맞아떨어진다.
data class ProductExposureDetailResponse(
    val productId: Long,
    val sku: String?,
    val name: String,
    val status: ProductStatus,
    val sellableStoreCount: Int,
    val exposedStoreCount: Int,
    val stores: List<StoreExposureResponse>,
) {
    companion object {
        fun from(detail: ProductExposureDetail): ProductExposureDetailResponse {
            val summary = detail.summary
            return ProductExposureDetailResponse(
                productId = summary.productId.value,
                sku = summary.sku?.value,
                name = summary.name,
                status = summary.status,
                sellableStoreCount = summary.sellableStoreCount,
                exposedStoreCount = summary.exposedStoreCount,
                stores = detail.stores.map(StoreExposureResponse::from),
            )
        }
    }
}

// exposed는 노출 판단(요구사항 3장)의 결과다. 점주의 숨김 여부(visibility)와 다르다.
// 노출되지 않는 매장은 품절 여부를 따질 대상이 아니라 stockStatus가 null이다.
data class StoreExposureResponse(
    val storeId: Long,
    val exposed: Boolean,
    val stockStatus: StockStatus?,
) {
    companion object {
        fun from(exposure: StoreExposure): StoreExposureResponse =
            when (val visibility = exposure.visibility) {
                StoreVisibility.NotVisible ->
                    StoreExposureResponse(exposure.storeId.value, exposed = false, stockStatus = null)
                is StoreVisibility.Visible ->
                    StoreExposureResponse(exposure.storeId.value, exposed = true, stockStatus = visibility.stockStatus)
            }
    }
}
