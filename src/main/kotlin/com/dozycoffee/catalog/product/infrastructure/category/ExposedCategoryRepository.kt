package com.dozycoffee.catalog.product.infrastructure.category

import com.dozycoffee.catalog.common.exposed.AdvisoryLockNamespace
import com.dozycoffee.catalog.common.exposed.DbNow
import com.dozycoffee.catalog.common.exposed.lockForTransaction
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
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
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
            .orderBy(CategoriesTable.displayOrder to SortOrder.ASC, CategoriesTable.id to SortOrder.ASC)
            .map { it.toCategory() }
            .toList()
    }

    override suspend fun hasChildren(id: CategoryId): Boolean =
        CategoriesTable
            .selectAll()
            .where { CategoriesTable.parentCategoryId eq id.value }
            .limit(1)
            .firstOrNull() != null

    override suspend fun lockSiblings(parentId: CategoryId?) {
        lockForTransaction(AdvisoryLockNamespace.CATEGORY_ORDER, parentId?.value ?: 0)
    }

    override suspend fun replaceOrder(orderedIds: List<CategoryId>) {
        orderedIds.forEachIndexed { index, id ->
            CategoriesTable.update({ CategoriesTable.id eq id.value }) {
                it[displayOrder] = index + 1
                it[updatedAt] = DbNow
            }
        }
    }

    override suspend fun insertTopLevel(name: String): TopLevelCategory {
        val order = nextDisplayOrder(parentId = null)
        val id =
            CategoriesTable.insert {
                it[CategoriesTable.name] = name
                it[displayOrder] = order
            }[CategoriesTable.id]
        return TopLevelCategory(CategoryId(id), name)
    }

    override suspend fun insertChild(
        name: String,
        parent: TopLevelCategory,
    ): ChildCategory {
        val order = nextDisplayOrder(parentId = parent.id)
        val id =
            CategoriesTable.insert {
                it[CategoriesTable.name] = name
                it[parentCategoryId] = parent.id.value
                it[displayOrder] = order
            }[CategoriesTable.id]
        return ChildCategory(CategoryId(id), name, parentId = parent.id)
    }

    override suspend fun save(category: Category): Category {
        val newParent = (category as? ChildCategory)?.parentId
        // 부모가 바뀌면(이동·승격·강등) 새 형제 맨 뒤로 보낸다. 이름만 바꿀 때는 순서를 건드리지 않는다.
        val movedOrder = if (currentParentOf(category.id) != newParent) nextDisplayOrder(newParent) else null
        CategoriesTable.update({ CategoriesTable.id eq category.id.value }) {
            it[name] = category.name
            it[parentCategoryId] = newParent?.value
            movedOrder?.let { order -> it[displayOrder] = order }
            it[updatedAt] = DbNow
        }
        return category
    }

    override suspend fun delete(id: CategoryId) {
        CategoriesTable.deleteWhere { CategoriesTable.id eq id.value }
    }

    private suspend fun currentParentOf(id: CategoryId): CategoryId? =
        CategoriesTable
            .select(CategoriesTable.parentCategoryId)
            .where { CategoriesTable.id eq id.value }
            .firstOrNull()
            ?.get(CategoriesTable.parentCategoryId)
            ?.let(::CategoryId)

    // 같은 부모 아래의 가장 큰 순서 값 + 1. 형제가 없으면 1이다. 동시에 같은 값을 받을 수 있지만 같은 값이면 id 순이라 문제없다.
    private suspend fun nextDisplayOrder(parentId: CategoryId?): Int {
        val max = CategoriesTable.displayOrder.max()
        val current =
            CategoriesTable
                .select(max)
                .where {
                    if (parentId ==
                        null
                    ) {
                        CategoriesTable.parentCategoryId.isNull()
                    } else {
                        CategoriesTable.parentCategoryId eq parentId.value
                    }
                }.firstOrNull()
                ?.get(max)
        return (current ?: 0) + 1
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
