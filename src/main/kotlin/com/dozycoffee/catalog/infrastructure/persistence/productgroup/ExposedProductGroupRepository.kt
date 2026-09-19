package com.dozycoffee.catalog.infrastructure.persistence.productgroup

import com.dozycoffee.catalog.domain.productgroup.ProductGroup
import com.dozycoffee.catalog.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.domain.productgroup.ProductGroupRepository
import com.dozycoffee.catalog.infrastructure.persistence.DbNow
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedProductGroupRepository : ProductGroupRepository {
    override suspend fun findById(id: ProductGroupId): ProductGroup? =
        ProductGroupsTable
            .selectAll()
            .where { ProductGroupsTable.id eq id.value }
            .firstOrNull()
            ?.toProductGroup()

    override suspend fun insert(name: String): ProductGroup {
        val id = ProductGroupsTable.insert { it[ProductGroupsTable.name] = name }[ProductGroupsTable.id]
        return ProductGroup(ProductGroupId(id), name)
    }

    override suspend fun save(productGroup: ProductGroup): ProductGroup {
        ProductGroupsTable.update({ ProductGroupsTable.id eq productGroup.id.value }) {
            it[name] = productGroup.name
            it[updatedAt] = DbNow
        }
        return productGroup
    }

    override suspend fun delete(id: ProductGroupId) {
        ProductGroupsTable.deleteWhere { ProductGroupsTable.id eq id.value }
    }

    private fun ResultRow.toProductGroup() = ProductGroup(ProductGroupId(this[ProductGroupsTable.id]), this[ProductGroupsTable.name])
}
