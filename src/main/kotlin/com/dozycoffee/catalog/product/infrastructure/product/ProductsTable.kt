package com.dozycoffee.catalog.product.infrastructure.product

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.infrastructure.category.CategoriesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList

// 상품 마스터. 판매 범위가 LIMITED면 대상 매장은 product_target_stores에 있다(빈 목록도 허용).
// 하위 테이블 5개와 매장 진열 설정·판매 가능 여부 테이블의 product_id FK가 이 테이블을 가리킨다.
object ProductsTable : Table("products") {
    val id = long("id").autoIncrement()
    val sku = varchar("sku", 64).nullable().uniqueIndex("products_sku_key")
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
    val basePrice = long("base_price").check("products_base_price_check") { it greaterEq 0L }
    val status =
        enumerationByName<ProductStatus>("status", 20)
            .default(ProductStatus.DRAFT)
            .check("products_status_check") { it inList ProductStatus.entries }

    // ALL / LIMITED. 도메인 StoreScope(sealed)와의 변환은 Repository 매퍼가 맡는다.
    val storeScope =
        varchar("store_scope", 20)
            .default("ALL")
            .check("products_store_scope_check") { it inList listOf("ALL", "LIMITED") }
    val tracksInventory = bool("tracks_inventory")
    val version = long("version").default(0)
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
