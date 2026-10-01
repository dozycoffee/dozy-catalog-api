package com.dozycoffee.catalog.store.presentation

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.application.availability.StoreProductAvailabilityApplicationService
import com.dozycoffee.catalog.store.application.display.StoreDisplaySettingApplicationService
import com.dozycoffee.catalog.store.application.storeproduct.StoreProductQueryService
import com.dozycoffee.catalog.store.presentation.dto.ChangeStockStatusRequest
import com.dozycoffee.catalog.store.presentation.dto.ChangeVisibilityRequest
import com.dozycoffee.catalog.store.presentation.dto.EffectiveOptionsResponse
import com.dozycoffee.catalog.store.presentation.dto.ReplaceDisplayOrderRequest
import com.dozycoffee.catalog.store.presentation.dto.StoreProductResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 가맹점주의 요청을 받은 Store 서비스가 부르는 매장 API(docs/api/store.md, ADR-0018). 인가는 경로 단위(catalog:store_agent)다.
// 매장 소유 확인은 Store 서비스가 하므로 경로의 storeId를 그대로 믿고, 그 매장이 상품을 취급할 수 있는지만 유스케이스가 확인한다.
@RestController
@RequestMapping("/api/v1/internal/stores/{storeId}/products")
class StoreProductInternalController(
    private val storeProductQueryService: StoreProductQueryService,
    private val displaySettingService: StoreDisplaySettingApplicationService,
    private val availabilityService: StoreProductAvailabilityApplicationService,
) {
    @GetMapping
    suspend fun list(
        @PathVariable storeId: Long,
        @RequestParam ids: String?,
    ): List<StoreProductResponse> =
        storeProductQueryService
            .listProducts(StoreId(storeId), ListParams.ids(ids, ::ProductId))
            .map(StoreProductResponse::from)

    // 응답은 변경 후 목록 전체다. 목록에 없는 상품의 순서도 함께 비워지므로 바뀐 것만 돌려줄 수 없다.
    @PutMapping("/display-order")
    suspend fun replaceDisplayOrder(
        @PathVariable storeId: Long,
        @RequestBody request: ReplaceDisplayOrderRequest,
    ): List<StoreProductResponse> {
        displaySettingService.replaceDisplayOrder(request.toCommand(StoreId(storeId)))
        return storeProductQueryService.listProducts(StoreId(storeId)).map(StoreProductResponse::from)
    }

    // 변경 유스케이스는 진열 설정·판매 가능 여부만 돌려주므로, 노출 판단을 반영한 응답은 변경 후 다시 조회해 만든다.
    // 변경 유스케이스는 상품 상태를 보지 않아(Active가 아닌 상품의 설정도 미리 바꿀 수 있음) 판매 범위 안의 Draft·단종 상품이면
    // 변경은 반영되고 응답은 404다. Store 서비스는 매장 상품 목록에 있는 상품만 바꾸므로, 그 사이 단종된 경우에만 생긴다.
    @PutMapping("/{productId}/visibility")
    suspend fun changeVisibility(
        @PathVariable storeId: Long,
        @PathVariable productId: Long,
        @RequestBody request: ChangeVisibilityRequest,
    ): StoreProductResponse {
        displaySettingService.changeVisibility(request.toCommand(StoreId(storeId), ProductId(productId)))
        return get(storeId, productId)
    }

    @PutMapping("/{productId}/stock-status")
    suspend fun changeStockStatus(
        @PathVariable storeId: Long,
        @PathVariable productId: Long,
        @RequestBody request: ChangeStockStatusRequest,
    ): StoreProductResponse {
        availabilityService.changeStockStatusByOwner(request.toCommand(StoreId(storeId), ProductId(productId)))
        return get(storeId, productId)
    }

    @GetMapping("/{productId}/effective-options")
    suspend fun effectiveOptions(
        @PathVariable storeId: Long,
        @PathVariable productId: Long,
    ): EffectiveOptionsResponse =
        EffectiveOptionsResponse.from(storeProductQueryService.getEffectiveOptions(StoreId(storeId), ProductId(productId)))

    private suspend fun get(
        storeId: Long,
        productId: Long,
    ): StoreProductResponse = StoreProductResponse.from(storeProductQueryService.getProduct(StoreId(storeId), ProductId(productId)))
}
