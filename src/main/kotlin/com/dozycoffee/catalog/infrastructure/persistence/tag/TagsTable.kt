package com.dozycoffee.catalog.infrastructure.persistence.tag

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import org.jetbrains.exposed.v1.core.Table

object TagsTable : Table("tags") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100).uniqueIndex("tags_name_key")
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
