package com.dozycoffee.catalog.exposure.presentation

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.common.web.PageResponse
import com.dozycoffee.catalog.common.web.toResponse
import com.dozycoffee.catalog.exposure.application.ProductExposureFilter
import com.dozycoffee.catalog.exposure.application.ProductExposureQueryService
import com.dozycoffee.catalog.exposure.presentation.dto.ProductExposureDetailResponse
import com.dozycoffee.catalog.exposure.presentation.dto.ProductExposureResponse
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 본사관리자의 상품 노출 현황 조회(docs/api/exposure.md, 요구사항 1.10). 인가는 경로 규칙(/admin/** = catalog:admin)이 맡는다.
@RestController
@RequestMapping("/api/v1/admin/product-exposures")
class ProductExposureController(
    private val queryService: ProductExposureQueryService,
) {
    @GetMapping
    suspend fun summarize(
        @RequestParam ids: String?,
        @RequestParam categoryId: Long?,
        @RequestParam tagId: Long?,
        @RequestParam groupId: Long?,
        @RequestParam page: Int?,
        @RequestParam size: Int?,
    ): PageResponse<ProductExposureResponse> {
        val filter =
            ProductExposureFilter(
                ids = ListParams.ids(ids, ::ProductId),
                categoryId = categoryId?.let(::CategoryId),
                tagId = tagId?.let(::TagId),
                groupId = groupId?.let(::ProductGroupId),
            )
        return queryService
            .summarize(filter, ListParams.pageRequest(page, size))
            .toResponse(ProductExposureResponse::from)
    }

    @GetMapping("/{productId}")
    suspend fun findDetail(
        @PathVariable productId: Long,
    ): ProductExposureDetailResponse = ProductExposureDetailResponse.from(queryService.findDetail(ProductId(productId)))
}
