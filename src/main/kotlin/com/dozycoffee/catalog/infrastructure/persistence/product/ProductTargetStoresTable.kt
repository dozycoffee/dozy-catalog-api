package com.dozycoffee.catalog.infrastructure.persistence.product

import com.dozycoffee.catalog.infrastructure.persistence.auditTimestamp
import org.jetbrains.exposed.v1.core.Table

// 판매 범위가 LIMITED일 때의 대상 매장. store_id는 Store BC 참조라 FK가 없다.
object ProductTargetStoresTable : Table("product_target_stores") {
    val productId = productIdReference("product_target_stores_product_id_fkey")
    val storeId = long("store_id")
    val createdAt = auditTimestamp("created_at")

    override val primaryKey = PrimaryKey(productId, storeId)
}
