package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId

// 상품에 연결된 옵션 그룹들의 노출 순서를 바꾼다(요구사항 1.9). 순서는 상품마다 독립적이다.
// order는 연결된 옵션 그룹 전체를 정확히 한 번씩 담아야 한다 — 일부만 담으면 Product가 거부한다.
data class ReorderOptionGroupsCommand(
    val productId: ProductId,
    val version: Long,
    val order: List<OptionGroupId>,
)
