package com.dozycoffee.catalog.infrastructure.persistence.product

import com.dozycoffee.catalog.infrastructure.persistence.auditTimestamp
import com.dozycoffee.catalog.infrastructure.persistence.category.CategoriesTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList

// 상품 루트. 판매 범위가 LIMITED면 대상 매장은 product_target_stores에 있다(빈 목록도 허용).
object ProductsTable : Table("products") {
    val id = long("id").autoIncrement()
    val sku = varchar("sku", 64).uniqueIndex("products_sku_key").nullable()
    val name = varchar("name", 100)
    val categoryId =
        long("category_id")
            .references(
                CategoriesTable.id,
                onDelete = ReferenceOption.NO_ACTION,
                onUpdate = ReferenceOption.NO_ACTION,
                fkName = "products_category_id_fkey",
            ).index("products_category_idx")
    val description = text("description").nullable()
    val imageUrl = varchar("image_url", 2048).nullable()
    val basePrice = long("base_price")
    val status = varchar("status", 20).default("DRAFT")
    val storeScope = varchar("store_scope", 20).default("ALL")
    val tracksInventory = bool("tracks_inventory")

    // 낙관적 잠금(docs/adr/0013). 저장할 때 WHERE version = ?로 확인하고 1 올린다.
    val version = long("version").default(0)
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        check("products_base_price_check") { basePrice greaterEq 0L }
        check("products_status_check") { status inList listOf("DRAFT", "ACTIVE", "DISCONTINUED") }
        check("products_store_scope_check") { storeScope inList listOf("ALL", "LIMITED") }
    }
}

// 하위 테이블의 product_id. 상품이 삭제되면 FK CASCADE로 함께 삭제된다.
internal fun Table.productIdReference(fkName: String): Column<Long> =
    long("product_id").references(
        ProductsTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = fkName,
    )
