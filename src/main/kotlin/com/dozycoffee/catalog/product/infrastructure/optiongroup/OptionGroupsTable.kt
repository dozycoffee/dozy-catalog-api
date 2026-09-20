package com.dozycoffee.catalog.product.infrastructure.optiongroup

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList

object OptionGroupsTable : Table("option_groups") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val selectionType = varchar("selection_type", 20)
    val required = bool("required")

    // 낙관적 잠금(docs/adr/0013). 저장할 때 WHERE version = ?로 확인하고 1 올린다.
    val version = long("version").default(0)
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        check("option_groups_selection_type_check") { selectionType inList listOf("SINGLE", "MULTI") }
    }
}
