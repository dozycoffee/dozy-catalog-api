package com.dozycoffee.catalog.product.presentation.product.dto

import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductOptionGroupLink
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.StoreScope

// 본사 API의 상품(docs/api/product.md `Product`). 다른 리소스는 ID로만 가리키고(카테고리·태그 이름은 담지 않음),
// 본사 API라 상품 그룹·판매 범위·버전까지 담는다. 대기 중인 예약은 담지 않는다(클라이언트가 예약 API에서 받아 합친다).
data class ProductResponse(
    val id: Long,
    val sku: String?,
    val name: String,
    val status: ProductStatus,
    val categoryId: Long,
    val description: String?,
    val imageUrl: String?,
    val basePrice: Long,
    val tracksInventory: Boolean,
    val tagIds: List<Long>,
    val groupIds: List<Long>,
    val storeScope: StoreScopeResponse,
    // 배열 순서가 이 상품에서의 노출 순서다.
    val optionGroups: List<ProductOptionGroupResponse>,
    val version: Long,
) {
    companion object {
        // 집합인 ID 목록은 응답이 요청마다 흔들리지 않도록 오름차순으로 준다.
        fun from(product: Product): ProductResponse =
            ProductResponse(
                id = product.id.value,
                sku = product.sku?.value,
                name = product.name,
                status = product.status,
                categoryId = product.categoryId.value,
                description = product.description,
                imageUrl = product.imageUrl,
                basePrice = product.basePrice.amount,
                tracksInventory = product.tracksInventory,
                tagIds = product.tagIds.map { it.value }.sorted(),
                groupIds = product.groupIds.map { it.value }.sorted(),
                storeScope = StoreScopeResponse.from(product.storeScope),
                optionGroups = product.optionGroupLinks.sortedBy { it.displayOrder }.map(ProductOptionGroupResponse::from),
                version = product.version,
            )
    }
}

// All도 targetStoreIds를 빈 배열로 준다(목록 필드는 null로 주지 않는다).
data class StoreScopeResponse(
    val kind: StoreScopeKind,
    val targetStoreIds: List<Long>,
) {
    companion object {
        fun from(scope: StoreScope): StoreScopeResponse =
            when (scope) {
                StoreScope.All -> StoreScopeResponse(StoreScopeKind.ALL, emptyList())
                is StoreScope.Limited -> StoreScopeResponse(StoreScopeKind.LIMITED, scope.targetStoreIds.map { it.value }.sorted())
            }
    }
}

data class ProductOptionGroupResponse(
    val optionGroupId: Long,
    val overrides: List<OptionOverrideResponse>,
) {
    companion object {
        fun from(link: ProductOptionGroupLink): ProductOptionGroupResponse =
            ProductOptionGroupResponse(
                optionGroupId = link.id.value,
                overrides = link.overrides.sortedBy { it.optionKey.value }.map(OptionOverrideResponse::from),
            )
    }
}

// EXCLUDE면 price가 null이다.
data class OptionOverrideResponse(
    val optionKey: String,
    val type: OptionOverrideType,
    val price: Long?,
) {
    companion object {
        fun from(override: OptionOverride): OptionOverrideResponse =
            when (override) {
                is OptionOverride.Price -> OptionOverrideResponse(override.optionKey.value, OptionOverrideType.PRICE, override.price.amount)
                is OptionOverride.Exclude -> OptionOverrideResponse(override.optionKey.value, OptionOverrideType.EXCLUDE, null)
            }
    }
}

enum class StoreScopeKind { ALL, LIMITED }

enum class OptionOverrideType { PRICE, EXCLUDE }
