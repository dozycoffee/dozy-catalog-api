package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.ProductId

// 이 상품의 옵션 예외(가격 변경·제외)를 해제한다(요구사항 1.9).
// 해제하면 그 옵션은 다시 옵션 그룹의 구성과 가격을 그대로 따른다.
data class RemoveOptionOverrideCommand(
    val productId: ProductId,
    val version: Long,
    val optionGroupId: OptionGroupId,
    val optionKey: OptionKey,
)
