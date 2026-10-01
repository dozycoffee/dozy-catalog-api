package com.dozycoffee.catalog.product.presentation.product

import com.dozycoffee.catalog.common.web.VersionHeaders
import com.dozycoffee.catalog.product.application.product.ProductOptionApplicationService
import com.dozycoffee.catalog.product.application.product.command.ExcludeOptionCommand
import com.dozycoffee.catalog.product.application.product.command.OverrideOptionPriceCommand
import com.dozycoffee.catalog.product.application.product.command.RemoveOptionOverrideCommand
import com.dozycoffee.catalog.product.application.product.command.UnlinkOptionGroupCommand
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.presentation.product.dto.EffectiveOptionsResponse
import com.dozycoffee.catalog.product.presentation.product.dto.LinkOptionGroupRequest
import com.dozycoffee.catalog.product.presentation.product.dto.ProductResponse
import com.dozycoffee.catalog.product.presentation.product.dto.ReorderOptionGroupsRequest
import com.dozycoffee.catalog.product.presentation.product.dto.SetOptionOverrideRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 상품의 옵션 그룹 연결·순서, 상품별 옵션 예외, 유효 옵션 구성(docs/api/product.md, 요구사항 1.9).
// 변경은 모두 화면이 보던 버전(If-Match)을 받고 변경 후 상품을 돌려준다(ADR-0013).
@RestController
@RequestMapping("$PRODUCTS_PATH/{productId}")
class ProductOptionController(
    private val productOptionService: ProductOptionApplicationService,
) {
    @PostMapping("/option-groups")
    suspend fun link(
        @PathVariable productId: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: LinkOptionGroupRequest,
    ): ResponseEntity<ProductResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        return ok(productOptionService.linkOptionGroup(request.toCommand(ProductId(productId), version)))
    }

    // 그 연결의 예외도 함께 사라지고, 다시 연결해도 복원되지 않는다.
    @DeleteMapping("/option-groups/{optionGroupId}")
    suspend fun unlink(
        @PathVariable productId: Long,
        @PathVariable optionGroupId: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ): ResponseEntity<ProductResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        val command = UnlinkOptionGroupCommand(ProductId(productId), version, OptionGroupId(optionGroupId))
        return ok(productOptionService.unlinkOptionGroup(command))
    }

    @PutMapping("/option-groups/order")
    suspend fun reorder(
        @PathVariable productId: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: ReorderOptionGroupsRequest,
    ): ResponseEntity<ProductResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        return ok(productOptionService.reorderOptionGroups(request.toCommand(ProductId(productId), version)))
    }

    // 같은 옵션 키에 기존 예외가 있으면 대체한다. 가격 예외와 제외는 유스케이스가 다르다.
    @PutMapping("/option-groups/{optionGroupId}/overrides/{optionKey}")
    suspend fun setOverride(
        @PathVariable productId: Long,
        @PathVariable optionGroupId: Long,
        @PathVariable optionKey: String,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: SetOptionOverrideRequest,
    ): ResponseEntity<ProductResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        val product =
            when (val override = request.toOverride(OptionKey(optionKey))) {
                is OptionOverride.Price ->
                    productOptionService.overrideOptionPrice(
                        OverrideOptionPriceCommand(
                            ProductId(productId),
                            version,
                            OptionGroupId(optionGroupId),
                            override.optionKey,
                            override.price,
                        ),
                    )
                is OptionOverride.Exclude ->
                    productOptionService.excludeOption(
                        ExcludeOptionCommand(ProductId(productId), version, OptionGroupId(optionGroupId), override.optionKey),
                    )
            }
        return ok(product)
    }

    // 해제하면 그 옵션은 다시 옵션 그룹의 구성과 가격을 따른다.
    @DeleteMapping("/option-groups/{optionGroupId}/overrides/{optionKey}")
    suspend fun removeOverride(
        @PathVariable productId: Long,
        @PathVariable optionGroupId: Long,
        @PathVariable optionKey: String,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ): ResponseEntity<ProductResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        val command = RemoveOptionOverrideCommand(ProductId(productId), version, OptionGroupId(optionGroupId), OptionKey(optionKey))
        return ok(productOptionService.removeOverride(command))
    }

    @GetMapping("/effective-options")
    suspend fun effectiveOptions(
        @PathVariable productId: Long,
    ): EffectiveOptionsResponse = EffectiveOptionsResponse.from(productOptionService.getEffectiveOptions(ProductId(productId)))
}
