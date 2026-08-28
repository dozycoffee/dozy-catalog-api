package com.dozycoffee.catalog.domain.product

import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductId

interface ProductRepository {
    suspend fun findById(id: ProductId): Product?

    // activate/discontinue 상태 전이 검증-쓰기 사이의 레이스를 막기 위해
    // SELECT ... FOR UPDATE로 구현한다.
    suspend fun findByIdForUpdate(id: ProductId): Product?

    suspend fun insert(newProduct: Product.NewProduct): Product

    suspend fun save(product: Product): Product

    suspend fun delete(id: ProductId)
}
