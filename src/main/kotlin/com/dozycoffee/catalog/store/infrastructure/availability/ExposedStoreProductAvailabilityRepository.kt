package com.dozycoffee.catalog.store.infrastructure.availability

import com.dozycoffee.catalog.common.exposed.DbNow
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.availability.AvailabilitySource
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailability
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert
import org.jetbrains.exposed.v1.r2dbc.upsertReturning
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class ExposedStoreProductAvailabilityRepository : StoreProductAvailabilityRepository {
    override suspend fun findById(id: StoreProductAvailabilityId): StoreProductAvailability? =
        StoreProductAvailabilitiesTable
            .selectAll()
            .where {
                (StoreProductAvailabilitiesTable.storeId eq id.storeId.value) and
                    (StoreProductAvailabilitiesTable.productId eq id.productId.value)
            }.firstOrNull()
            ?.toAvailability()

    override suspend fun findAllByStore(storeId: StoreId): List<StoreProductAvailability> =
        StoreProductAvailabilitiesTable
            .selectAll()
            .where { StoreProductAvailabilitiesTable.storeId eq storeId.value }
            .orderBy(StoreProductAvailabilitiesTable.productId)
            .map { it.toAvailability() }
            .toList()

    override suspend fun findAllByProduct(productId: ProductId): List<StoreProductAvailability> =
        StoreProductAvailabilitiesTable
            .selectAll()
            .where { StoreProductAvailabilitiesTable.productId eq productId.value }
            .orderBy(StoreProductAvailabilitiesTable.storeId)
            .map { it.toAvailability() }
            .toList()

    // 점주의 최신 의도가 이기므로 조건 없이 INSERT … ON CONFLICT (store_id, product_id) DO UPDATE로 덮어쓴다.
    // 출처는 상품의 재고 추적 여부로 정해져 바뀌지 않으므로 갱신하지 않는다.
    override suspend fun saveByOwner(availability: StoreProductAvailability) {
        require(availability.source == AvailabilitySource.OWNER) { "점주 저장은 OWNER 출처만 받습니다: ${availability.id}" }
        StoreProductAvailabilitiesTable.upsert(
            StoreProductAvailabilitiesTable.storeId,
            StoreProductAvailabilitiesTable.productId,
            onUpdate = {
                it[StoreProductAvailabilitiesTable.stockStatus] = insertValue(StoreProductAvailabilitiesTable.stockStatus)
                it[StoreProductAvailabilitiesTable.updatedAt] = DbNow
            },
        ) { it.assign(availability) }
    }

    // INSERT … ON CONFLICT (store_id, product_id) DO UPDATE … WHERE last_event_at IS NULL OR last_event_at < 새 이벤트 시각.
    // 비교 대상인 새 이벤트 시각은 EXCLUDED.last_event_at과 같은 값이라 파라미터로 바로 비교한다.
    // 조건에 걸려 갱신하지 않으면 RETURNING이 행을 돌려주지 않으므로, 돌려받은 행이 있는지로 반영 여부를 판단한다.
    override suspend fun saveInventoryEvent(availability: StoreProductAvailability): Boolean {
        require(availability.source == AvailabilitySource.INVENTORY) { "재고 이벤트 저장은 INVENTORY 출처만 받습니다: ${availability.id}" }
        val occurredAt =
            requireNotNull(availability.lastEventAt) { "반영한 재고 이벤트 시각이 없습니다: ${availability.id}" }.toOffsetDateTime()
        return StoreProductAvailabilitiesTable
            .upsertReturning(
                StoreProductAvailabilitiesTable.storeId,
                StoreProductAvailabilitiesTable.productId,
                returning = listOf(StoreProductAvailabilitiesTable.storeId),
                onUpdate = {
                    it[StoreProductAvailabilitiesTable.stockStatus] = insertValue(StoreProductAvailabilitiesTable.stockStatus)
                    it[StoreProductAvailabilitiesTable.lastEventAt] = insertValue(StoreProductAvailabilitiesTable.lastEventAt)
                    it[StoreProductAvailabilitiesTable.updatedAt] = DbNow
                },
                where = {
                    StoreProductAvailabilitiesTable.lastEventAt.isNull() or
                        (StoreProductAvailabilitiesTable.lastEventAt less occurredAt)
                },
            ) { it.assign(availability) }
            .toList()
            .isNotEmpty()
    }

    override suspend fun deleteAll(ids: Collection<StoreProductAvailabilityId>) {
        if (ids.isEmpty()) return
        StoreProductAvailabilitiesTable.deleteWhere {
            (StoreProductAvailabilitiesTable.storeId to StoreProductAvailabilitiesTable.productId) inList
                ids.map { it.storeId.value to it.productId.value }
        }
    }

    private fun UpdateBuilder<*>.assign(availability: StoreProductAvailability) {
        this[StoreProductAvailabilitiesTable.storeId] = availability.id.storeId.value
        this[StoreProductAvailabilitiesTable.productId] = availability.id.productId.value
        this[StoreProductAvailabilitiesTable.availabilitySource] = availability.source
        this[StoreProductAvailabilitiesTable.stockStatus] = availability.stockStatus
        this[StoreProductAvailabilitiesTable.lastEventAt] = availability.lastEventAt?.toOffsetDateTime()
    }

    private fun ResultRow.toAvailability() =
        StoreProductAvailability(
            id =
                StoreProductAvailabilityId(
                    StoreId(this[StoreProductAvailabilitiesTable.storeId]),
                    ProductId(this[StoreProductAvailabilitiesTable.productId]),
                ),
            source = this[StoreProductAvailabilitiesTable.availabilitySource],
            stockStatus = this[StoreProductAvailabilitiesTable.stockStatus],
            lastEventAt = this[StoreProductAvailabilitiesTable.lastEventAt]?.toInstant(),
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime = atOffset(ZoneOffset.UTC)
}
