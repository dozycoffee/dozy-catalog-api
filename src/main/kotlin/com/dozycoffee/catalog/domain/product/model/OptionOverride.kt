package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.domain.optiongroup.OptionKey

// product_option_overrides의 override_type CHECK 제약(PRICE면 price 필수,
// EXCLUDE면 price 없음)을 타입으로 표현해 애초에 불일치 상태를 만들 수 없게 한다.
sealed class OptionOverride {
    abstract val optionKey: OptionKey

    data class Price(
        override val optionKey: OptionKey,
        val price: Money,
    ) : OptionOverride()

    data class Exclude(
        override val optionKey: OptionKey,
    ) : OptionOverride()
}
