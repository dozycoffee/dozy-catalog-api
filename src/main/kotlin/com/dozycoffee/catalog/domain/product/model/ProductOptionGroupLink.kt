package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
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

    internal fun replaceOverrides(newOverrides: List<OptionOverride>) {
        this.overrides = newOverrides
    }
}
