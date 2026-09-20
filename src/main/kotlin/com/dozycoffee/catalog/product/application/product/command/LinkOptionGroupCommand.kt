package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId

// 상품에 옵션 그룹을 연결한다(요구사항 1.9). 새 연결은 기존 연결들 뒤에 붙고, 순서는
// ReorderOptionGroupsCommand로 바꾼다. version은 화면이 보고 있던 버전이다(ADR-0013).
data class LinkOptionGroupCommand(
    val productId: ProductId,
    val version: Long,
    val optionGroupId: OptionGroupId,
)
