package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.ProductId

// 특정 옵션의 가격을 이 상품에서만 다르게 지정한다(요구사항 1.9). 가격은 Money가 0 이상으로 강제한다.
// 같은 옵션 키에 기존 예외가 있으면 새 예외로 대체된다.
data class OverrideOptionPriceCommand(
    val productId: ProductId,
    val version: Long,
    val optionGroupId: OptionGroupId,
    val optionKey: OptionKey,
    val price: Money,
)
