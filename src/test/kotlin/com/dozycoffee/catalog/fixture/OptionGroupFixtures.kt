package com.dozycoffee.catalog.fixture

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 옵션 이름은 옵션 키와 같게 둔다.
fun option(
    key: String,
    price: Long = 0,
) = Option(OptionKey(key), name = key, price = Money(price))

fun optionGroup(
    id: Long,
    vararg options: Option,
    name: String = "옵션 그룹 $id",
    selectionType: SelectionType = SelectionType.SINGLE,
    required: Boolean = true,
) = OptionGroup(
    id = OptionGroupId(id),
    name = name,
    selectionType = selectionType,
    required = required,
    options = options.toList(),
)
