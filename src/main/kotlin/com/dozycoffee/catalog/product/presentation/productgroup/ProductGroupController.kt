package com.dozycoffee.catalog.product.presentation.productgroup

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.product.application.productgroup.ProductGroupApplicationService
import com.dozycoffee.catalog.product.application.productgroup.command.RenameProductGroupCommand
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.presentation.productgroup.dto.ProductGroupNameRequest
import com.dozycoffee.catalog.product.presentation.productgroup.dto.ProductGroupResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 상품 그룹 API(docs/api/product.md 상품 그룹, 요구사항 1.8).
@RestController
@RequestMapping("/api/v1/admin/product-groups")
class ProductGroupController(
    private val productGroupService: ProductGroupApplicationService,
) {
    // 등록 순.
    @GetMapping
    suspend fun list(
        @RequestParam ids: String?,
    ): List<ProductGroupResponse> = productGroupService.list(ListParams.ids(ids) { ProductGroupId(it) }).map(ProductGroupResponse::from)

    @PostMapping
    suspend fun register(
        @RequestBody request: ProductGroupNameRequest,
    ): ResponseEntity<ProductGroupResponse> {
        val group = productGroupService.register(request.name)
        return ResponseEntity
            .created(URI.create("$BASE_PATH/${group.id.value}"))
            .body(ProductGroupResponse.from(group))
    }

    @PutMapping("/{productGroupId}")
    suspend fun rename(
        @PathVariable productGroupId: Long,
        @RequestBody request: ProductGroupNameRequest,
    ): ProductGroupResponse {
        val group = productGroupService.rename(RenameProductGroupCommand(ProductGroupId(productGroupId), request.name))
        return ProductGroupResponse.from(group)
    }

    // 참조하던 모든 상품에서 자동으로 빠진다.
    @DeleteMapping("/{productGroupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(
        @PathVariable productGroupId: Long,
    ) {
        productGroupService.delete(ProductGroupId(productGroupId))
    }

    private companion object {
        const val BASE_PATH = "/api/v1/admin/product-groups"
    }
}
