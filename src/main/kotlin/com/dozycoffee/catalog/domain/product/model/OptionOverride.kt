package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.shared.Money

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

// 상품별 예외를 반영했을 때 선택 가능한 옵션(제외되지 않은 옵션)을 옵션 그룹의 순서대로 돌려준다.
// 제외 검증(Product), 옵션 목록 교체 검증(OptionReplacementPolicy), 유효 옵션 구성(EffectiveOptionResolver)이
// 같은 기준을 쓰도록 model에 둔다. service가 model을 쓰는 방향만 남기기 위해서다.
internal fun selectableOptions(
    options: List<Option>,
    overrides: List<OptionOverride>,
): List<Option> {
    val excludedKeys = overrides.filterIsInstance<OptionOverride.Exclude>().map { it.optionKey }.toSet()
    return options.filterNot { it.optionKey in excludedKeys }
}
