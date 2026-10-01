package com.dozycoffee.catalog.product.presentation.optiongroup.dto

import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 옵션 그룹(docs/api/product.md 옵션 그룹). version은 낙관적 잠금 버전이다(ADR-0013).
data class OptionGroupResponse(
    val id: Long,
    val name: String,
    val selectionType: SelectionType,
    val required: Boolean,
    val options: List<OptionResponse>,
    val version: Long,
) {
    companion object {
        fun from(optionGroup: OptionGroup): OptionGroupResponse =
            OptionGroupResponse(
                id = optionGroup.id.value,
                name = optionGroup.name,
                selectionType = optionGroup.selectionType,
                required = optionGroup.required,
                options = optionGroup.options.map(OptionResponse::from),
                version = optionGroup.version,
            )
    }
}

data class OptionResponse(
    val optionKey: String,
    val name: String,
    val price: Long,
) {
    companion object {
        fun from(option: Option): OptionResponse = OptionResponse(option.optionKey.value, option.name, option.price.amount)
    }
}
