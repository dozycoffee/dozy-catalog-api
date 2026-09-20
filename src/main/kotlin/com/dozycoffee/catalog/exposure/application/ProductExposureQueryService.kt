package com.dozycoffee.catalog.exposure.application

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.exposure.application.port.ProductExposureQueryPort
import com.dozycoffee.catalog.exposure.application.port.ProductExposureRecord
import com.dozycoffee.catalog.exposure.application.port.StoreDirectoryPort
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.Sku
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.policy.ProductVisibilityPolicy
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import org.springframework.stereotype.Service

// 본사관리자가 보는 상품 노출 현황(요구사항 1.10). 쓰기가 없는 조회 전용이라 이 모듈에는 domain이 없고,
// 조회 포트와 그 결과를 합치는 이 서비스만 있다(ADR-0015).
// 매출·판매량 같은 거래 데이터는 다루지 않는다. 노출 상태만 센다.
@Service
class ProductExposureQueryService(
    private val exposureQuery: ProductExposureQueryPort,
    private val storeDirectory: StoreDirectoryPort,
    private val transactionRunner: TransactionRunner,
) {
    // 상품별 판매 가능 매장 수 대비 노출 중인 매장 수.
    suspend fun summarize(filter: ProductExposureFilter = ProductExposureFilter()): List<ProductExposureSummary> {
        // 외부 호출(Store BC)은 DB 트랜잭션 밖에서 먼저 끝낸다.
        val allStoreIds = storeDirectory.findAllIds()
        val records = transactionRunner.inTransaction { exposureQuery.findAll(filter) }
        return records.map { record -> record.summary(exposures(record, allStoreIds)) }
    }

    // 특정 상품의 매장별 노출 상태. 요약과 같은 재료·같은 판단을 쓰므로 두 결과는 항상 맞아떨어진다.
    suspend fun findDetail(productId: ProductId): ProductExposureDetail {
        val allStoreIds = storeDirectory.findAllIds()
        val record =
            transactionRunner.inTransaction { exposureQuery.find(productId) }
                ?: throw ProductNotFoundException(productId)
        val exposures = exposures(record, allStoreIds)
        return ProductExposureDetail(summary = record.summary(exposures), stores = exposures)
    }

    // 판매 가능한 매장마다 요구사항 3장의 판단을 돌린다. 판단 자체는 ProductVisibilityPolicy에만 있고
    // 여기서는 매장 목록을 정해 넘기기만 한다 — 조회 경로가 점주 화면과 다른 답을 내면 안 되기 때문이다.
    // 매장 수 × 상품 수만큼 판단하므로, 규모가 커지면 요약만 집계 쿼리로 바꾼다.
    private fun exposures(
        record: ProductExposureRecord,
        allStoreIds: Set<StoreId>,
    ): List<StoreExposure> {
        val settings = record.storeSettings.associateBy { it.storeId }
        return record.sellableStoreIds(allStoreIds).map { storeId ->
            val setting = settings[storeId]
            StoreExposure(
                storeId = storeId,
                visibility =
                    ProductVisibilityPolicy.resolve(
                        status = record.status,
                        storeScope = record.storeScope,
                        tracksInventory = record.tracksInventory,
                        storeId = storeId,
                        visibility = setting?.visibility,
                        stockStatus = setting?.stockStatus,
                    ),
            )
        }
    }

    // 판매 범위가 All이면 전체 매장, Limited면 대상 매장이 판매 가능 매장이다(요구사항 1.5, 1.10).
    // 대상 매장이 비어 있는 Limited 상품은 판매 가능 매장이 하나도 없다.
    private fun ProductExposureRecord.sellableStoreIds(allStoreIds: Set<StoreId>): List<StoreId> =
        when (val scope = storeScope) {
            StoreScope.All -> allStoreIds
            is StoreScope.Limited -> scope.targetStoreIds
        }.sortedBy { it.value }

    private fun ProductExposureRecord.summary(exposures: List<StoreExposure>) =
        ProductExposureSummary(
            productId = productId,
            sku = sku,
            name = name,
            status = status,
            sellableStoreCount = exposures.size,
            exposedStoreCount = exposures.count { it.visibility is StoreVisibility.Visible },
        )
}

// 요약 한 줄. 노출 중인 매장은 품절이어도 센다 — 품절은 노출된 뒤의 구매 가능 여부라 노출 여부와 독립이다(요구사항 2.5).
data class ProductExposureSummary(
    val productId: ProductId,
    val sku: Sku?,
    val name: String,
    val status: ProductStatus,
    val sellableStoreCount: Int,
    val exposedStoreCount: Int,
)

// 상세. 요약 수치와 그 수치를 만든 매장별 노출 상태를 함께 담는다.
data class ProductExposureDetail(
    val summary: ProductExposureSummary,
    val stores: List<StoreExposure>,
)

data class StoreExposure(
    val storeId: StoreId,
    val visibility: StoreVisibility,
)
