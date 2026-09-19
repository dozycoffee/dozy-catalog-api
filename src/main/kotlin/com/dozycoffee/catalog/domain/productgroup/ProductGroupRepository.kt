package com.dozycoffee.catalog.domain.productgroup

interface ProductGroupRepository {
    suspend fun findById(id: ProductGroupId): ProductGroup?

    suspend fun insert(name: String): ProductGroup

    suspend fun save(productGroup: ProductGroup): ProductGroup

    // 상품과의 연결(product_groups_map)은 FK CASCADE로 함께 삭제된다(요구사항 1.8).
    suspend fun delete(id: ProductGroupId)
}
