package com.dozycoffee.catalog.infrastructure.persistence.product

import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.infrastructure.persistence.auditTimestamp
import com.dozycoffee.catalog.infrastructure.persistence.category.CategoriesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList

// 상품 마스터. 매장 진열 설정·판매 가능 여부 테이블의 product_id FK가 이 테이블을 가리키므로 먼저 정의한다.
// Exposed 스키마 검사는 FK의 대상 테이블이 Exposed에 정의되어 있어야 동작한다.
// 하위 테이블과 Repository는 Product Repository 구현(#51) 때 추가한다.
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
