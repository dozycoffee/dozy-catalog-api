package com.dozycoffee.catalog.infrastructure.persistence.category

import com.dozycoffee.catalog.infrastructure.persistence.auditTimestamp
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or

// parent_category_id가 NULL이면 대분류, 값이 있으면 그 대분류의 소분류다(2단계 계층).
object CategoriesTable : Table("categories") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val parentCategoryId =
        long("parent_category_id")
            .references(
                id,
                onDelete = ReferenceOption.NO_ACTION,
                onUpdate = ReferenceOption.NO_ACTION,
                fkName = "categories_parent_category_id_fkey",
            ).nullable()
            .index("categories_parent_idx")
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        check("categories_not_self_parent") { parentCategoryId.isNull() or (parentCategoryId neq id) }
    }
}
