package com.dozycoffee.catalog.product.presentation.internal.dto

import com.dozycoffee.catalog.product.application.product.query.ProductStatePage
import com.dozycoffee.catalog.product.application.product.query.ProductSummaryView
import com.dozycoffee.catalog.product.domain.category.Category
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.TopLevelCategory
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.StoreScope

// Store 서비스용 내부 조회 API의 응답(docs/api/store-service.md). 상품 그룹, 낙관적 잠금 버전은 담지 않는다.

// 상품 기준 정보. 판매 범위와 상태는 담지 않는다 — 그건 이벤트와 상태 목록으로 받는다.
data class InternalProductResponse(
    val productId: Long,
    val sku: String?,
    val name: String,
    val categoryId: Long,
    val imageUrl: String?,
    val basePrice: Long,
    val tracksInventory: Boolean,
    val displayStartingPrice: Long,
    val tags: List<InternalTagResponse>,
) {
    companion object {
        fun from(view: ProductSummaryView): InternalProductResponse =
            InternalProductResponse(
                productId = view.product.id.value,
                sku = view.product.sku?.value,
                name = view.product.name,
                categoryId = view.product.categoryId.value,
                imageUrl = view.product.imageUrl,
                basePrice = view.product.basePrice.amount,
                tracksInventory = view.product.tracksInventory,
                displayStartingPrice = view.displayStartingPrice.amount,
                tags = view.tags.map { InternalTagResponse(it.id.value, it.name) },
            )
    }
}

data class InternalTagResponse(
    val id: Long,
    val name: String,
)

// 카테고리 트리. 대분류마다 소분류를 담는다. 순서는 노출 순서다.
data class InternalCategoryResponse(
    val id: Long,
    val name: String,
    val children: List<InternalChildCategoryResponse>,
) {
    companion object {
        // 평평한 목록(대분류·소분류 섞임, 노출 순서)을 트리로 묶는다. 부모가 없는 소분류는 없다(FK).
        fun treeOf(categories: List<Category>): List<InternalCategoryResponse> {
            val children = categories.filterIsInstance<ChildCategory>().groupBy { it.parentId }
            return categories.filterIsInstance<TopLevelCategory>().map { top ->
                InternalCategoryResponse(
                    id = top.id.value,
                    name = top.name,
                    children = children[top.id].orEmpty().map { InternalChildCategoryResponse(it.id.value, it.name) },
                )
            }
        }
    }
}

data class InternalChildCategoryResponse(
    val id: Long,
    val name: String,
)

// 상품 상태. 이벤트(docs/events/product.md)의 data와 같은 모양과 같은 sequence다.
data class ProductStateResponse(
    val productId: Long,
    val sku: String?,
    val status: ProductStatus,
    val storeScope: StoreScopeResponse,
    val tracksInventory: Boolean,
    val sequence: Long,
) {
    companion object {
        fun from(product: Product): ProductStateResponse =
            ProductStateResponse(
                productId = product.id.value,
                sku = product.sku?.value,
                status = product.status,
                storeScope = StoreScopeResponse.from(product.storeScope),
                tracksInventory = product.tracksInventory,
                sequence = product.version,
            )
    }
}

// ALL이면 targetStoreIds는 null이다. LIMITED이면 비어 있어도 배열이다.
data class StoreScopeResponse(
    val kind: String,
    val targetStoreIds: List<Long>?,
) {
    companion object {
        fun from(scope: StoreScope): StoreScopeResponse =
            when (scope) {
                StoreScope.All -> StoreScopeResponse("ALL", null)
                is StoreScope.Limited -> StoreScopeResponse("LIMITED", scope.targetStoreIds.map { it.value }.sorted())
            }
    }
}

// 커서 방식의 페이지. nextCursor가 null이면 마지막이다. 다음 요청의 cursor에 그대로 넣는다.
data class ProductStatePageResponse(
    val content: List<ProductStateResponse>,
    val nextCursor: Long?,
) {
    companion object {
        fun from(page: ProductStatePage): ProductStatePageResponse =
            ProductStatePageResponse(page.products.map(ProductStateResponse::from), page.nextCursor?.value)
    }
}
