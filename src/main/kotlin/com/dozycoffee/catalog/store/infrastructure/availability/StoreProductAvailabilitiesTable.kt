package com.dozycoffee.catalog.store.infrastructure.availability

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import com.dozycoffee.catalog.product.infrastructure.product.ProductsTable
import com.dozycoffee.catalog.store.domain.availability.AvailabilitySource
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// row 부재 = 출처별 기본값(INVENTORY는 품절, OWNER는 판매중).
object StoreProductAvailabilitiesTable : Table("store_product_availabilities") {
    val storeId = long("store_id")
    val productId =
        long("product_id")
            .references(
                ProductsTable.id,
                onDelete = ReferenceOption.CASCADE,
                onUpdate = ReferenceOption.NO_ACTION,
                fkName = "store_product_availabilities_product_id_fkey",
            ).index("store_product_availabilities_product_idx")

    // 컬럼 이름은 source다. Table이 이미 source 멤버를 가지고 있어 속성 이름만 다르게 짓는다.
    val availabilitySource =
        enumerationByName<AvailabilitySource>("source", 20)
            .check("store_product_availabilities_source_check") { it inList AvailabilitySource.entries }
    val stockStatus =
        enumerationByName<StockStatus>("stock_status", 20)
            .check("store_product_availabilities_stock_status_check") { it inList StockStatus.entries }

    // 마지막으로 반영한 재고 이벤트의 발생 시각(INVENTORY만). 조건부 upsert의 비교 기준이다.
    val lastEventAt = timestampWithTimeZone("last_event_at").nullable()
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(storeId, productId)

    init {
        check("store_product_availabilities_owner_has_no_event") {
            (availabilitySource eq AvailabilitySource.INVENTORY) or lastEventAt.isNull()
        }
    }
}
