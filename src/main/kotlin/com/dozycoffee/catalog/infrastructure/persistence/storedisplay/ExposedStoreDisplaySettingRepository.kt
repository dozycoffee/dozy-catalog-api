package com.dozycoffee.catalog.infrastructure.persistence.storedisplay

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.storedisplay.StoreDisplaySettingRepository
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySetting
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySettingId
import com.dozycoffee.catalog.infrastructure.persistence.DbNow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
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

    override suspend fun saveDisplayOrder(setting: StoreDisplaySetting) {
        StoreDisplaySettingsTable.update({ StoreDisplaySettingsTable.id eq setting.id.value }) {
            it[displayOrder] = setting.displayOrder
            it[updatedAt] = DbNow
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
