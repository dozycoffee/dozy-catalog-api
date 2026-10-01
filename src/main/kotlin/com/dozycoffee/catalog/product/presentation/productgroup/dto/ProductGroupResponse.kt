package com.dozycoffee.catalog.product.presentation.productgroup.dto

import com.dozycoffee.catalog.product.domain.productgroup.ProductGroup

// 상품 그룹(docs/api/product.md 상품 그룹). 본사 내부용이라 내부 API 응답에는 담지 않는다(요구사항 1.8).
data class ProductGroupResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(group: ProductGroup): ProductGroupResponse = ProductGroupResponse(group.id.value, group.name)
    }
}
