package com.dozycoffee.catalog.domain.productgroup

interface ProductGroupRepository {
    suspend fun findById(id: ProductGroupId): ProductGroup?

    suspend fun insert(name: String): ProductGroup

    suspend fun save(productGroup: ProductGroup): ProductGroup

    suspend fun delete(id: ProductGroupId)
}
