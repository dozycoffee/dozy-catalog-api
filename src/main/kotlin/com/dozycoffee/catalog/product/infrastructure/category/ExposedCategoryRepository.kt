package com.dozycoffee.catalog.product.infrastructure.category

import com.dozycoffee.catalog.common.exposed.DbNow
import com.dozycoffee.catalog.product.domain.category.Category
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.category.CategoryRepository
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.TopLevelCategory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedCategoryRepository : CategoryRepository {
    override suspend fun findById(id: CategoryId): Category? =
        CategoriesTable
            .selectAll()
            .where { CategoriesTable.id eq id.value }
            .firstOrNull()
            ?.toCategory()

    override suspend fun findTopLevelById(id: CategoryId): TopLevelCategory? = findById(id) as? TopLevelCategory

    // 대분류인 행만 잠근다. 소분류 행이면 잠그지 않고 null을 돌려준다.
    override suspend fun findTopLevelByIdForUpdate(id: CategoryId): TopLevelCategory? =
        CategoriesTable
            .selectAll()
            .where { (CategoriesTable.id eq id.value) and CategoriesTable.parentCategoryId.isNull() }
            .forUpdate()
            .firstOrNull()
            ?.toCategory() as? TopLevelCategory

    override suspend fun findAll(
        ids: Set<CategoryId>?,
        parentId: CategoryId?,
        topLevelOnly: Boolean,
    ): List<Category> {
        val conditions = mutableListOf<Op<Boolean>>()
        ids?.let { ids -> conditions += if (ids.isEmpty()) Op.FALSE else CategoriesTable.id inList ids.map { it.value } }
        parentId?.let { parent -> conditions += CategoriesTable.parentCategoryId eq parent.value }
        if (topLevelOnly) conditions += CategoriesTable.parentCategoryId.isNull()
        return CategoriesTable
            .selectAll()
            .where { conditions.fold(Op.TRUE as Op<Boolean>) { acc, condition -> acc and condition } }
            .orderBy(CategoriesTable.id)
            .map { it.toCategory() }
            .toList()
    }

    override suspend fun hasChildren(id: CategoryId): Boolean =
        CategoriesTable
            .selectAll()
            .where { CategoriesTable.parentCategoryId eq id.value }
            .limit(1)
            .firstOrNull() != null

    override suspend fun insertTopLevel(name: String): TopLevelCategory {
        val id = CategoriesTable.insert { it[CategoriesTable.name] = name }[CategoriesTable.id]
        return TopLevelCategory(CategoryId(id), name)
    }

    override suspend fun insertChild(
        name: String,
        parent: TopLevelCategory,
    ): ChildCategory {
        val id =
            CategoriesTable.insert {
                it[CategoriesTable.name] = name
                it[parentCategoryId] = parent.id.value
            }[CategoriesTable.id]
        return ChildCategory(CategoryId(id), name, parentId = parent.id)
    }

    override suspend fun save(category: Category): Category {
        CategoriesTable.update({ CategoriesTable.id eq category.id.value }) {
            it[name] = category.name
            it[parentCategoryId] = (category as? ChildCategory)?.parentId?.value
            it[updatedAt] = DbNow
        }
        return category
    }

    override suspend fun delete(id: CategoryId) {
        CategoriesTable.deleteWhere { CategoriesTable.id eq id.value }
    }

    private fun ResultRow.toCategory(): Category {
        val id = CategoryId(this[CategoriesTable.id])
        val name = this[CategoriesTable.name]
        return when (val parentId = this[CategoriesTable.parentCategoryId]) {
            null -> TopLevelCategory(id, name)
            else -> ChildCategory(id, name, parentId = CategoryId(parentId))
        }
    }
}
