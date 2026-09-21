package com.dozycoffee.catalog.product.domain.productgroup

interface ProductGroupRepository {
    suspend fun findById(id: ProductGroupId): ProductGroup?

    // 목록 조회(요구사항 1.8). 등록 순(id)으로 돌려준다. ids가 비어 있으면 빈 목록이고, 없는 ID는 빠진다.
    suspend fun findAll(ids: Set<ProductGroupId>? = null): List<ProductGroup>

    suspend fun insert(name: String): ProductGroup

    suspend fun save(productGroup: ProductGroup): ProductGroup

    // 상품과의 연결(product_groups_map)은 FK CASCADE로 함께 삭제된다(요구사항 1.8).
    suspend fun delete(id: ProductGroupId)
}
