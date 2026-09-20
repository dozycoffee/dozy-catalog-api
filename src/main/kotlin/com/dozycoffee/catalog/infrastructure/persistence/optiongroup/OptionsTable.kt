package com.dozycoffee.catalog.infrastructure.persistence.optiongroup

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq

// 옵션 목록은 항상 통째로 교체되므로 id는 재발급된다. 상품별 예외는 option_key로 참조한다.
object OptionsTable : Table("options") {
    val id = long("id").autoIncrement()
    val optionGroupId =
        long("option_group_id").references(
            OptionGroupsTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "options_option_group_id_fkey",
        )
    val optionKey = varchar("option_key", 64)
    val name = varchar("name", 100)
    val price = long("price")
    val displayOrder = integer("display_order")
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("options_key_unique_in_group", optionGroupId, optionKey)
        check("options_price_check") { price greaterEq 0L }
    }
}
