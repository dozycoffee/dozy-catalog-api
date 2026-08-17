package com.dozycoffee.catalog.domain.optiongroup

// PUT/예약 스냅샷으로 물리적 id가 재발급돼도 Product의 optionOverrides
// 참조가 끊기지 않도록 유지되는 논리 식별자. 그룹 내에서 유일해야 한다.
@JvmInline
value class OptionKey(
    val value: String,
)
