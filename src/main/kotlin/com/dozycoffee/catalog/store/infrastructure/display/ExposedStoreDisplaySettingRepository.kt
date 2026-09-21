package com.dozycoffee.catalog.store.infrastructure.display

import com.dozycoffee.catalog.common.exposed.AdvisoryLockNamespace
import com.dozycoffee.catalog.common.exposed.DbNow
import com.dozycoffee.catalog.common.exposed.lockForTransaction
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.display.StoreDisplayOrder
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySetting
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingId
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.r2dbc.batchUpsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsertReturning
import org.springframework.stereotype.Repository

@Repository
class ExposedStoreDisplaySettingRepository : StoreDisplaySettingRepository {
    override suspend fun findById(id: StoreDisplaySettingId): StoreDisplaySetting? =
        StoreDisplaySettingsTable
            .selectAll()
            .where { StoreDisplaySettingsTable.id eq id.value }
            .firstOrNull()
            ?.toSetting()

    override suspend fun findByStoreAndProduct(
        storeId: StoreId,
        productId: ProductId,
    ): StoreDisplaySetting? =
        StoreDisplaySettingsTable
            .selectAll()
            .where { (StoreDisplaySettingsTable.storeId eq storeId.value) and (StoreDisplaySettingsTable.productId eq productId.value) }
            .firstOrNull()
            ?.toSetting()

    override suspend fun findAllByStore(storeId: StoreId): List<StoreDisplaySetting> =
        StoreDisplaySettingsTable
            .selectAll()
            .where { StoreDisplaySettingsTable.storeId eq storeId.value }
            .orderBy(StoreDisplaySettingsTable.id)
            .map { it.toSetting() }
            .toList()

    override suspend fun findAllByProduct(productId: ProductId): List<StoreDisplaySetting> =
        StoreDisplaySettingsTable
            .selectAll()
            .where { StoreDisplaySettingsTable.productId eq productId.value }
            .orderBy(StoreDisplaySettingsTable.id)
            .map { it.toSetting() }
            .toList()

    // INSERT … ON CONFLICT (store_id, product_id) DO UPDATE … RETURNING으로 한 문장에서 만들거나 기존 행을 돌려준다.
    // DO NOTHING은 충돌하면 행을 돌려주지 않아 다시 조회해야 하므로, 값이 그대로인 UPDATE(store_id = EXCLUDED.store_id)로
    // 항상 행을 돌려받는다. 이 UPDATE는 행을 잠그므로 같은 매장·상품의 진열 설정 변경은 트랜잭션이 끝날 때까지 줄을 선다.
    override suspend fun findOrCreate(
        storeId: StoreId,
        productId: ProductId,
    ): StoreDisplaySetting =
        StoreDisplaySettingsTable
            .upsertReturning(
                StoreDisplaySettingsTable.storeId,
                StoreDisplaySettingsTable.productId,
                onUpdate = { it[StoreDisplaySettingsTable.storeId] = insertValue(StoreDisplaySettingsTable.storeId) },
            ) {
                it[StoreDisplaySettingsTable.storeId] = storeId.value
                it[StoreDisplaySettingsTable.productId] = productId.value
            }.single()
            .toSetting()

    override suspend fun saveVisibility(setting: StoreDisplaySetting) {
        StoreDisplaySettingsTable.update({ StoreDisplaySettingsTable.id eq setting.id.value }) {
            it[visibility] = setting.visibility
            it[updatedAt] = DbNow
        }
    }

    override suspend fun lockStoreDisplayOrder(storeId: StoreId) {
        lockForTransaction(AdvisoryLockNamespace.STORE_DISPLAY_ORDER, storeId.value)
    }

    // 목록에 없는 상품의 순서를 한 문장으로 비우고, 목록의 상품은 INSERT … ON CONFLICT (store_id, product_id) DO UPDATE로
    // 번호를 넣는다. 새로 만드는 행은 노출 여부가 기본값(노출)이고, 이미 있는 행은 진열 순서만 바꾼다.
    override suspend fun replaceDisplayOrder(order: StoreDisplayOrder) {
        val listedProductIds = order.productIds.map { it.value }
        StoreDisplaySettingsTable.update({
            val othersWithOrder =
                (StoreDisplaySettingsTable.storeId eq order.storeId.value) and StoreDisplaySettingsTable.displayOrder.isNotNull()
            if (listedProductIds.isEmpty()) {
                othersWithOrder
            } else {
                othersWithOrder and
                    (StoreDisplaySettingsTable.productId notInList listedProductIds)
            }
        }) {
            it[displayOrder] = null
            it[updatedAt] = DbNow
        }
        if (listedProductIds.isEmpty()) return
        StoreDisplaySettingsTable.batchUpsert(
            order.numbered,
            StoreDisplaySettingsTable.storeId,
            StoreDisplaySettingsTable.productId,
            onUpdate = {
                it[StoreDisplaySettingsTable.displayOrder] = insertValue(StoreDisplaySettingsTable.displayOrder)
                it[StoreDisplaySettingsTable.updatedAt] = DbNow
            },
            shouldReturnGeneratedValues = false,
        ) { (productId, displayOrder) ->
            this[StoreDisplaySettingsTable.storeId] = order.storeId.value
            this[StoreDisplaySettingsTable.productId] = productId.value
            this[StoreDisplaySettingsTable.displayOrder] = displayOrder
        }
    }

    override suspend fun deleteAll(ids: Collection<StoreDisplaySettingId>) {
        if (ids.isEmpty()) return
        StoreDisplaySettingsTable.deleteWhere { StoreDisplaySettingsTable.id inList ids.map { it.value } }
    }

    private fun ResultRow.toSetting() =
        StoreDisplaySetting(
            id = StoreDisplaySettingId(this[StoreDisplaySettingsTable.id]),
            storeId = StoreId(this[StoreDisplaySettingsTable.storeId]),
            productId = ProductId(this[StoreDisplaySettingsTable.productId]),
            displayOrder = this[StoreDisplaySettingsTable.displayOrder],
            visibility = this[StoreDisplaySettingsTable.visibility],
        )
}
