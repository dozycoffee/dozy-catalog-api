package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.shared.Entity

// Product 애그리거트 내부 엔티티. product_option_groups의 PK는 product_id +
// option_group_id 복합키라, Product 안에서는 optionGroupId 자체가 식별자 역할을 한다.
class ProductOptionGroupLink internal constructor(
    optionGroupId: OptionGroupId,
    displayOrder: Int,
    overrides: List<OptionOverride> = emptyList(),
) : Entity<OptionGroupId>(optionGroupId) {
    var displayOrder: Int = displayOrder
        internal set
    var overrides: List<OptionOverride> = overrides
        private set

    // 이 상품에서 선택할 수 없게 제외한 옵션 키. 옵션 그룹의 옵션 중 여기에 없는 것이 선택 가능한
    // 옵션이다. 제외 검증(Product)과 application 정책(유효 옵션 구성, 옵션 목록 교체)이 모두
    // 이 기준을 쓴다.
    val excludedOptionKeys: Set<OptionKey>
        get() = overrides.filterIsInstance<OptionOverride.Exclude>().map { it.optionKey }.toSet()

    internal fun replaceOverrides(newOverrides: List<OptionOverride>) {
        this.overrides = newOverrides
    }
}
