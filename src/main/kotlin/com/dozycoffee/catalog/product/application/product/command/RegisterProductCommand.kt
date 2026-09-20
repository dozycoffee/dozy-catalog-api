package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId

// 상품 등록 입력(요구사항 1.2). 태그는 이름으로 받는다 — 같은 이름이 있으면 재사용하고 없으면 만든다(1.7).
// SKU와 상태(Draft)는 시스템이 정하므로 입력에 없다.
data class RegisterProductCommand(
    val name: String,
    val categoryId: CategoryId,
    val basePrice: Money,
    val tracksInventory: Boolean,
    val description: String? = null,
    val imageUrl: String? = null,
    val tagNames: List<String> = emptyList(),
    val groupIds: Set<ProductGroupId> = emptySet(),
    val optionGroupIds: List<OptionGroupId> = emptyList(),
)
