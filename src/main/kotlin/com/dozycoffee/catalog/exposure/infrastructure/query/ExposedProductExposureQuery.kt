package com.dozycoffee.catalog.exposure.infrastructure.query

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.exposure.application.ProductExposureFilter
import com.dozycoffee.catalog.exposure.application.port.ProductExposureQueryPort
import com.dozycoffee.catalog.exposure.application.port.ProductExposureRecord
import com.dozycoffee.catalog.exposure.application.port.StoreSettingRecord
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.Sku
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.infrastructure.category.CategoriesTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductGroupsMapTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductTagsTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductTargetStoresTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductsTable
import com.dozycoffee.catalog.store.infrastructure.availability.StoreProductAvailabilitiesTable
import com.dozycoffee.catalog.store.infrastructure.display.StoreDisplaySettingsTable
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Component

// 노출 현황 조회는 상품·대상 매장·진열 설정·판매 가능 여부를 한꺼번에 봐야 해 애그리거트 하나에 담기지 않는다.
// 조회 전용이라 Repository로 애그리거트를 불러오지 않고 테이블을 직접 읽는다(ADR-0012, 패키지 구조의 조회 포트).
// 트랜잭션은 열지 않는다 — application의 TransactionRunner 안에서 호출된다.
@Component
class ExposedProductExposureQuery : ProductExposureQueryPort {
    override suspend fun findAll(filter: ProductExposureFilter): List<ProductExposureRecord> =
        toRecords(
            ProductsTable
                .selectAll()
                .where { filter.toCondition() }
                .orderBy(ProductsTable.id to SortOrder.ASC)
                .toList(),
        )

    override suspend fun find(productId: ProductId): ProductExposureRecord? =
        toRecords(
            ProductsTable
                .selectAll()
                .where { ProductsTable.id eq productId.value }
                .toList(),
        ).singleOrNull()

    // 지정한 조건만 AND로 붙인다. 카테고리는 소분류로도 대분류로도 줄 수 있어, 그 대분류의 소분류에 속한 상품까지 포함한다
    // (상품은 소분류만 참조하므로 대분류로 거르면 아무것도 나오지 않게 되기 때문이다).
    private fun ProductExposureFilter.toCondition(): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        categoryId?.let { category ->
            val childCategoryIds =
                CategoriesTable
                    .select(CategoriesTable.id)
                    .where { CategoriesTable.parentCategoryId eq category.value }
            conditions += (ProductsTable.categoryId eq category.value) or (ProductsTable.categoryId inSubQuery childCategoryIds)
        }
        tagId?.let { tag ->
            val taggedProductIds =
                ProductTagsTable
                    .select(ProductTagsTable.productId)
                    .where { ProductTagsTable.tagId eq tag.value }
            conditions += ProductsTable.id inSubQuery taggedProductIds
        }
        groupId?.let { group ->
            val groupedProductIds =
                ProductGroupsMapTable
                    .select(ProductGroupsMapTable.productId)
                    .where { ProductGroupsMapTable.groupId eq group.value }
            conditions += ProductsTable.id inSubQuery groupedProductIds
        }
        return conditions.fold(Op.TRUE as Op<Boolean>) { acc, condition -> acc and condition }
    }

    // 하위 테이블은 상품 수와 상관없이 테이블마다 한 번씩만 조회한다.
    private suspend fun toRecords(roots: List<ResultRow>): List<ProductExposureRecord> {
        if (roots.isEmpty()) return emptyList()
        val ids = roots.map { it[ProductsTable.id] }
        val targetStores =
            ProductTargetStoresTable
                .selectAll()
                .where { ProductTargetStoresTable.productId inList ids }
                .toList()
                .groupBy({ it[ProductTargetStoresTable.productId] }, { StoreId(it[ProductTargetStoresTable.storeId]) })
        val storeSettings = findStoreSettings(ids)
        return roots.map { row ->
            val id = row[ProductsTable.id]
            ProductExposureRecord(
                productId = ProductId(id),
                sku = row[ProductsTable.sku]?.let(::Sku),
                name = row[ProductsTable.name],
                status = row[ProductsTable.status],
                storeScope =
                    when (val scope = row[ProductsTable.storeScope]) {
                        STORE_SCOPE_ALL -> StoreScope.All
                        STORE_SCOPE_LIMITED -> StoreScope.Limited(targetStores[id].orEmpty().toSet())
                        else -> error("알 수 없는 판매 범위: $scope")
                    },
                tracksInventory = row[ProductsTable.tracksInventory],
                storeSettings = storeSettings[id].orEmpty(),
            )
        }
    }

    // 진열 설정과 판매 가능 여부는 서로 다른 테이블이고 한쪽만 있을 수 있어, 매장 단위로 합친 뒤 상품별로 묶는다.
    private suspend fun findStoreSettings(productIds: List<Long>): Map<Long, List<StoreSettingRecord>> {
        val visibilities =
            StoreDisplaySettingsTable
                .selectAll()
                .where { StoreDisplaySettingsTable.productId inList productIds }
                .toList()
                .associate {
                    (it[StoreDisplaySettingsTable.productId] to it[StoreDisplaySettingsTable.storeId]) to
                        it[StoreDisplaySettingsTable.visibility]
                }
        val stockStatuses =
            StoreProductAvailabilitiesTable
                .selectAll()
                .where { StoreProductAvailabilitiesTable.productId inList productIds }
                .toList()
                .associate {
                    (it[StoreProductAvailabilitiesTable.productId] to it[StoreProductAvailabilitiesTable.storeId]) to
                        it[StoreProductAvailabilitiesTable.stockStatus]
                }
        return (visibilities.keys + stockStatuses.keys)
            .groupBy({ (productId, _) -> productId }) { key ->
                StoreSettingRecord(
                    storeId = StoreId(key.second),
                    visibility = visibilities[key],
                    stockStatus = stockStatuses[key],
                )
            }
    }

    private companion object {
        const val STORE_SCOPE_ALL = "ALL"
        const val STORE_SCOPE_LIMITED = "LIMITED"
    }
}
