package com.dozycoffee.catalog.product.presentation.product

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.common.web.PageResponse
import com.dozycoffee.catalog.common.web.VersionHeaders
import com.dozycoffee.catalog.common.web.toResponse
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.query.ProductQueryService
import com.dozycoffee.catalog.product.application.product.query.ProductSearchFilter
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.presentation.product.dto.ChangeStoreScopeRequest
import com.dozycoffee.catalog.product.presentation.product.dto.ProductResponse
import com.dozycoffee.catalog.product.presentation.product.dto.RegisterProductRequest
import com.dozycoffee.catalog.product.presentation.product.dto.ReplaceProductRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 본사관리자의 상품 등록·조회·교체·삭제, 상태 전환, 판매 범위 변경(docs/api/product.md 상품, 요구사항 1.2~1.5, 1.11, 1.12).
// 옵션 그룹 연결·예외와 유효 옵션 구성은 ProductOptionController가 맡는다.
// 상품 단건 응답에는 본문의 version과 같은 값을 ETag로 붙인다(ADR-0013).
@RestController
@RequestMapping(PRODUCTS_PATH)
class ProductController(
    private val productService: ProductApplicationService,
    private val productQueryService: ProductQueryService,
) {
    @PostMapping
    suspend fun register(
        @RequestBody request: RegisterProductRequest,
    ): ResponseEntity<ProductResponse> {
        val product = productService.register(request.toCommand())
        return ResponseEntity
            .created(URI.create("$PRODUCTS_PATH/${product.id.value}"))
            .eTag("\"${product.version}\"")
            .body(ProductResponse.from(product))
    }

    // 최근 등록한 상품이 먼저 온다. categoryId는 대분류도 받는다(그 아래 소분류의 상품을 모두 포함).
    @GetMapping
    suspend fun search(
        @RequestParam ids: String?,
        @RequestParam keyword: String?,
        @RequestParam categoryId: Long?,
        @RequestParam tagId: Long?,
        @RequestParam groupId: Long?,
        @RequestParam status: ProductStatus?,
        @RequestParam page: Int?,
        @RequestParam size: Int?,
    ): PageResponse<ProductResponse> {
        val pageRequest = ListParams.pageRequest(page, size)
        val filter =
            ProductSearchFilter(
                ids = ListParams.ids(ids, ::ProductId),
                keyword = keyword,
                categoryId = categoryId?.let(::CategoryId),
                tagId = tagId?.let(::TagId),
                groupId = groupId?.let(::ProductGroupId),
                status = status,
            )
        return productQueryService.search(filter, pageRequest).toResponse(ProductResponse::from)
    }

    @GetMapping("/{productId}")
    suspend fun get(
        @PathVariable productId: Long,
    ): ResponseEntity<ProductResponse> = ok(productService.get(ProductId(productId)))

    // 상태, 재고 추적 여부, 판매 범위, 옵션 그룹 연결·예외는 바꾸지 않는다(각 전용 엔드포인트).
    @PutMapping("/{productId}")
    suspend fun replace(
        @PathVariable productId: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: ReplaceProductRequest,
    ): ResponseEntity<ProductResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        return ok(productService.replace(request.toCommand(ProductId(productId), version)))
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(
        @PathVariable productId: Long,
    ) {
        productService.delete(ProductId(productId))
    }

    // 상태 전환은 버전을 받지 않는다. 서버가 행 잠금으로 중복 전이를 막는다.
    @PostMapping("/{productId}/activate")
    suspend fun activate(
        @PathVariable productId: Long,
    ): ResponseEntity<ProductResponse> = ok(productService.activate(ProductId(productId)))

    @PostMapping("/{productId}/discontinue")
    suspend fun discontinue(
        @PathVariable productId: Long,
    ): ResponseEntity<ProductResponse> = ok(productService.discontinue(ProductId(productId)))

    // 대상에서 빠진 매장의 개별 설정과 수동 품절은 같은 요청 안에서 삭제된다(요구사항 1.5).
    @PutMapping("/{productId}/store-scope")
    suspend fun changeStoreScope(
        @PathVariable productId: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: ChangeStoreScopeRequest,
    ): ResponseEntity<ProductResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        return ok(productService.changeStoreScope(request.toCommand(ProductId(productId), version)))
    }
}

internal const val PRODUCTS_PATH = "/api/v1/admin/products"

internal fun ok(product: Product): ResponseEntity<ProductResponse> =
    VersionHeaders.okWithVersion(ProductResponse.from(product), product.version)
