package com.dozycoffee.catalog.domain.optiongroup

import com.dozycoffee.catalog.domain.shared.Money

// options.id(물리 PK)를 참조하는 곳이 스키마에 없고(product_option_overrides는
// option_group_id + option_key로 참조), PUT이 항상 옵션 전체를 배치로 교체하므로
// domain에서는 물리 id 없이 Value Object로 다룬다.
data class Option(
    val optionKey: OptionKey,
    val name: String,
    val price: Money,
)
