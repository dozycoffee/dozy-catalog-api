package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.ProductId

// 특정 옵션을 이 상품에서는 선택할 수 없게 제외한다(요구사항 1.9).
// 이 제외로 선택 가능한 옵션이 0개가 되면 Product가 거부한다.
data class ExcludeOptionCommand(
    val productId: ProductId,
    val version: Long,
    val optionGroupId: OptionGroupId,
    val optionKey: OptionKey,
)
