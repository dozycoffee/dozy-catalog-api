package com.dozycoffee.catalog.product.infrastructure.query

import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.product.application.port.ProductSearchCondition
import com.dozycoffee.catalog.product.application.port.ProductSearchOrder
import com.dozycoffee.catalog.product.application.port.ProductSearchQueryPort
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.infrastructure.category.CategoriesTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductGroupsMapTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductTagsTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductTargetStoresTable
import com.dozycoffee.catalog.product.infrastructure.product.ProductsTable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.select
import org.springframework.stereotype.Component

// 상품 목록 검색. 조건에 맞는 상품 ID만 골라 페이지로 돌려주고, 애그리거트 복원은 application이 ProductRepository로 한다.
// 포트 구현은 Repository를 부르지 않고 테이블을 직접 읽는다(docs/architecture/package-structure.md).
@Component
class ExposedProductSearchQuery : ProductSearchQueryPort {
    override suspend fun search(
        condition: ProductSearchCondition,
        pageRequest: PageRequest,
    ): Page<ProductId> {
        val total =
            ProductsTable
                .select(ProductsTable.id)
                .where { condition.toOp() }
                .count()
        // 마지막 페이지를 지난 요청은 내용 없이 전체 건수만 돌려준다.
        if (pageRequest.offset >= total) return Page.of(emptyList(), pageRequest, total)
        val ids =
            ProductsTable
                .select(ProductsTable.id)
                .where { condition.toOp() }
                .orderBy(ProductsTable.id to condition.order.toSortOrder())
                .limit(pageRequest.size)
                .offset(pageRequest.offset)
                .map { ProductId(it[ProductsTable.id]) }
                .toList()
        return Page.of(ids, pageRequest, total)
    }

    // 지정한 조건만 AND로 붙인다.
    private fun ProductSearchCondition.toOp(): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        ids?.let { ids ->
            conditions += if (ids.isEmpty()) Op.FALSE else ProductsTable.id inList ids.map { it.value }
        }
        keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            // 상품명의 %, _ 는 와일드카드가 아니라 글자로 찾는다.
            val literal = LikePattern.ofLiteral(keyword.lowercase())
            val namePattern = LikePattern("%${literal.pattern}%", literal.escapeChar)
            conditions += (ProductsTable.name.lowerCase() like namePattern) or (ProductsTable.sku eq keyword)
        }
        // 상품은 소분류만 참조하므로, 대분류를 받으면 그 아래 소분류를 참조하는 상품까지 포함한다(노출 현황 필터와 같은 규칙).
        categoryId?.let { category ->
            val childCategoryIds =
                CategoriesTable
                    .select(CategoriesTable.id)
                    .where { CategoriesTable.parentCategoryId eq category.value }
            conditions += (ProductsTable.categoryId eq category.value) or (ProductsTable.categoryId inSubQuery childCategoryIds)
        }
        tagId?.let { tag ->
            val taggedProductIds =
                ProductTagsTable
                    .select(ProductTagsTable.productId)
                    .where { ProductTagsTable.tagId eq tag.value }
            conditions += ProductsTable.id inSubQuery taggedProductIds
        }
        groupId?.let { group ->
            val groupedProductIds =
                ProductGroupsMapTable
                    .select(ProductGroupsMapTable.productId)
                    .where { ProductGroupsMapTable.groupId eq group.value }
            conditions += ProductsTable.id inSubQuery groupedProductIds
        }
        status?.let { status -> conditions += ProductsTable.status eq status }
        coveredStoreIds?.let { storeIds ->
            conditions +=
                if (storeIds.isEmpty()) {
                    // 전체 판매 상품도 매장이 하나도 없으면 보여 줄 곳이 없다.
                    Op.FALSE
                } else {
                    val targetedProductIds =
                        ProductTargetStoresTable
                            .select(ProductTargetStoresTable.productId)
                            .where { ProductTargetStoresTable.storeId inList storeIds.map { it.value } }
                    (ProductsTable.storeScope eq STORE_SCOPE_ALL) or (ProductsTable.id inSubQuery targetedProductIds)
                }
        }
        return conditions.fold(Op.TRUE as Op<Boolean>) { acc, condition -> acc and condition }
    }

    private fun ProductSearchOrder.toSortOrder(): SortOrder =
        when (this) {
            ProductSearchOrder.NEWEST_FIRST -> SortOrder.DESC
            ProductSearchOrder.OLDEST_FIRST -> SortOrder.ASC
        }

    private companion object {
        const val STORE_SCOPE_ALL = "ALL"
    }
}
