package com.dozycoffee.catalog.product.presentation.internal

import com.dozycoffee.catalog.common.web.InvalidRequestException
import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.product.query.InternalProductQueryService
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.presentation.internal.dto.InternalCategoryResponse
import com.dozycoffee.catalog.product.presentation.internal.dto.InternalProductResponse
import com.dozycoffee.catalog.product.presentation.internal.dto.ProductStatePageResponse
import com.dozycoffee.catalog.product.presentation.product.dto.EffectiveOptionsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// Store 서비스가 부르는 상품 조회 API(docs/api/store-service.md, ADR-0020). 인가는 경로 단위(catalog:store_agent)다.
// 매장을 모르는 조회라 경로에 storeId가 없다. 응답에 상품 그룹, 낙관적 잠금 버전, 판매 범위를 담지 않는다(상태 목록의 storeScope 제외).
@RestController
@RequestMapping("/api/v1/internal")
class InternalProductController(
    private val queryService: InternalProductQueryService,
    private val categoryService: CategoryApplicationService,
) {
    // ids는 필수다(최대 100개). 전체 상품을 한 번에 주지 않는다 — 처음 채우기는 product-states를 쓴다.
    @GetMapping("/products")
    suspend fun products(
        @RequestParam ids: String?,
    ): List<InternalProductResponse> {
        val productIds = ListParams.ids(ids, ::ProductId) ?: throw InvalidRequestException("ids는 필수입니다")
        return queryService.summaries(productIds).map(InternalProductResponse::from)
    }

    @GetMapping("/categories")
    suspend fun categories(): List<InternalCategoryResponse> = InternalCategoryResponse.treeOf(categoryService.list())

    @GetMapping("/products/{productId}/effective-options")
    suspend fun effectiveOptions(
        @PathVariable productId: Long,
    ): EffectiveOptionsResponse = EffectiveOptionsResponse.from(queryService.getEffectiveOptions(ProductId(productId)))

    @GetMapping("/product-states")
    suspend fun productStates(
        @RequestParam cursor: Long?,
        @RequestParam size: Int?,
    ): ProductStatePageResponse {
        val resolvedSize = size ?: DEFAULT_STATE_PAGE_SIZE
        if (resolvedSize !in 1..MAX_STATE_PAGE_SIZE) {
            throw InvalidRequestException("size는 1 이상 ${MAX_STATE_PAGE_SIZE} 이하여야 합니다: $resolvedSize")
        }
        if (cursor != null && cursor <= 0) throw InvalidRequestException("cursor는 상품 ID여야 합니다: $cursor")
        return ProductStatePageResponse.from(queryService.states(cursor?.let(::ProductId), resolvedSize))
    }

    private companion object {
        const val DEFAULT_STATE_PAGE_SIZE = 200
        const val MAX_STATE_PAGE_SIZE = 500
    }
}
