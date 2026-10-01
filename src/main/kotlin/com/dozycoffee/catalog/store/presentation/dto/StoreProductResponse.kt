package com.dozycoffee.catalog.store.presentation.dto

import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.application.storeproduct.StoreProductView
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.display.Visibility

// 한 매장에서 한 상품의 상태와, 그 상품을 알아볼 상품 기준 정보(docs/api/store.md 매장 상품).
// 점주와 손님에게 그대로 전달될 수 있어 본사 내부 정보(상품 그룹, 판매 범위, 상태, 버전)는 담지 않는다(ADR-0018).
data class StoreProductResponse(
    val productId: Long,
    val sku: String?,
    val name: String,
    val categoryId: Long,
    val imageUrl: String?,
    val basePrice: Long,
    val tracksInventory: Boolean,
    val displayOrder: Int?,
    val visibility: Visibility,
    // 숨긴 상품은 손님에게 보이지 않아 판매중·품절을 담지 않는다.
    val stockStatus: StockStatus?,
) {
    companion object {
        // 매장 상품은 Active이고 판매 범위에 든 상품뿐이라 노출 판단의 비노출은 점주가 숨긴 것이다(StoreProductView).
        fun from(view: StoreProductView): StoreProductResponse {
            val product = view.product
            return StoreProductResponse(
                productId = product.id.value,
                sku = product.sku?.value,
                name = product.name,
                categoryId = product.categoryId.value,
                imageUrl = product.imageUrl,
                basePrice = product.basePrice.amount,
                tracksInventory = product.tracksInventory,
                displayOrder = view.displayOrder,
                visibility =
                    when (view.visibility) {
                        is StoreVisibility.Visible -> Visibility.VISIBLE
                        StoreVisibility.NotVisible -> Visibility.HIDDEN
                    },
                stockStatus = (view.visibility as? StoreVisibility.Visible)?.stockStatus,
            )
        }
    }
}
