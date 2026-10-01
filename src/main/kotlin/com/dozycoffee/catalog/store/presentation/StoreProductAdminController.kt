package com.dozycoffee.catalog.store.presentation

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.application.storeproduct.StoreProductQueryService
import com.dozycoffee.catalog.store.presentation.dto.StoreProductResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 본사관리자가 한 매장의 상품 상태를 보는 API(docs/api/store.md 본사관리자의 매장 상품 조회). 인가는 경로 단위(catalog:admin)다.
// 매장 상품 목록과 같은 조회 로직과 응답을 쓰고 경로와 호출자만 다르다(docs/api/README.md 경로와 인가).
@RestController
@RequestMapping("/api/v1/admin/stores/{storeId}/products")
class StoreProductAdminController(
    private val storeProductQueryService: StoreProductQueryService,
) {
    @GetMapping
    suspend fun list(
        @PathVariable storeId: Long,
        @RequestParam ids: String?,
    ): List<StoreProductResponse> =
        storeProductQueryService
            .listProducts(StoreId(storeId), ListParams.ids(ids, ::ProductId))
            .map(StoreProductResponse::from)
}
