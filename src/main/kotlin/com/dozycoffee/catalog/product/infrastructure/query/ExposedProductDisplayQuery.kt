package com.dozycoffee.catalog.product.infrastructure.query

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.application.port.ProductDisplayInfo
import com.dozycoffee.catalog.product.application.port.ProductDisplayQueryPort
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.Sku
import com.dozycoffee.catalog.product.infrastructure.product.ProductsTable
import com.dozycoffee.catalog.product.infrastructure.product.loadOptionGroupLinks
import com.dozycoffee.catalog.product.infrastructure.product.loadTagIds
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.select
import org.springframework.stereotype.Component

// Store 서비스용 표시 정보 조회. 상품 테이블의 표시용 컬럼과 태그·옵션 그룹 연결만 읽고 판매 범위(`product_target_stores`)는
// 읽지 않는다. 하위 테이블은 상품 수와 상관없이 테이블마다 한 번씩만 조회한다.
@Component
class ExposedProductDisplayQuery : ProductDisplayQueryPort {
    override suspend fun findAll(ids: Set<ProductId>): List<ProductDisplayInfo> {
        if (ids.isEmpty()) return emptyList()
        val roots =
            ProductsTable
                .select(
                    ProductsTable.id,
                    ProductsTable.sku,
                    ProductsTable.name,
                    ProductsTable.categoryId,
                    ProductsTable.imageUrl,
                    ProductsTable.basePrice,
                    ProductsTable.tracksInventory,
                ).where { ProductsTable.id inList ids.map { it.value } }
                .orderBy(ProductsTable.id to SortOrder.ASC)
                .toList()
        if (roots.isEmpty()) return emptyList()
        val rootIds = roots.map { it[ProductsTable.id] }
        val tags = loadTagIds(rootIds)
        val links = loadOptionGroupLinks(rootIds)
        return roots.map { row ->
            val id = row[ProductsTable.id]
            ProductDisplayInfo(
                id = ProductId(id),
                sku = row[ProductsTable.sku]?.let(::Sku),
                name = row[ProductsTable.name],
                categoryId = CategoryId(row[ProductsTable.categoryId]),
                imageUrl = row[ProductsTable.imageUrl],
                basePrice = Money(row[ProductsTable.basePrice]),
                tracksInventory = row[ProductsTable.tracksInventory],
                tagIds = tags[id].orEmpty().toSet(),
                optionGroupLinks = links[id].orEmpty(),
            )
        }
    }

    override suspend fun findById(id: ProductId): ProductDisplayInfo? = findAll(setOf(id)).singleOrNull()
}
