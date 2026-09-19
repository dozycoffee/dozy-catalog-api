package com.dozycoffee.catalog.infrastructure.persistence.storedisplay

import com.dozycoffee.catalog.domain.storedisplay.model.Visibility
import com.dozycoffee.catalog.infrastructure.persistence.auditTimestamp
import com.dozycoffee.catalog.infrastructure.persistence.product.ProductsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList

// row 부재 = 기본값(노출). 점주가 처음 바꿀 때 생긴다(Lazy 생성).
object StoreDisplaySettingsTable : Table("store_display_settings") {
    val id = long("id").autoIncrement()
    val storeId = long("store_id")
    val productId =
        long("product_id")
            .references(
                ProductsTable.id,
                onDelete = ReferenceOption.CASCADE,
                onUpdate = ReferenceOption.NO_ACTION,
                fkName = "store_display_settings_product_id_fkey",
            ).index("store_display_settings_product_idx")
    val displayOrder = integer("display_order").nullable()
    val visibility =
        enumerationByName<Visibility>("visibility", 20)
            .default(Visibility.VISIBLE)
            .check("store_display_settings_visibility_check") { it inList Visibility.entries }
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("store_display_settings_store_product", storeId, productId)
    }
}
