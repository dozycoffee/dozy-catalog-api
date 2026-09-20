package com.dozycoffee.catalog.product.infrastructure.productgroup

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import org.jetbrains.exposed.v1.core.Table

object ProductGroupsTable : Table("product_groups") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
