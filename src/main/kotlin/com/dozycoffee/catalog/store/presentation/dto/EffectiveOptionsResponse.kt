package com.dozycoffee.catalog.store.presentation.dto

import com.dozycoffee.catalog.product.application.policy.EffectiveOptionConfig
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 매장 내부 API의 유효 옵션 구성(docs/api/store.md effective-options, 요구사항 1.9).
// 지금은 본사 API의 응답과 모양이 같지만, 호출자(Store 서비스)가 다른 별도 계약이라 따로 두고 따로 바꾼다.
data class EffectiveOptionsResponse(
    val productId: Long,
    val basePrice: Long,
    val displayStartingPrice: Long,
    val groups: List<EffectiveOptionGroupResponse>,
) {
    companion object {
        fun from(config: EffectiveOptionConfig) =
            EffectiveOptionsResponse(
                productId = config.productId.value,
                basePrice = config.basePrice.amount,
                displayStartingPrice = config.displayStartingPrice.amount,
                groups = config.groups.map(EffectiveOptionGroupResponse::from),
            )
    }
}

data class EffectiveOptionGroupResponse(
    val optionGroupId: Long,
    val name: String,
    val selectionType: SelectionType,
    val required: Boolean,
    val autoSelectedOptionKey: String?,
    val options: List<EffectiveOptionResponse>,
) {
    companion object {
        fun from(group: EffectiveOptionGroup) =
            EffectiveOptionGroupResponse(
                optionGroupId = group.optionGroupId.value,
                name = group.name,
                selectionType = group.selectionType,
                required = group.required,
                autoSelectedOptionKey = group.autoSelectedOption?.optionKey?.value,
                options =
                    group.options.map { option ->
                        EffectiveOptionResponse(
                            optionKey = option.optionKey.value,
                            name = option.name,
                            price = option.price.amount,
                            priceOverridden = option.priceOverridden,
                        )
                    },
            )
    }
}

data class EffectiveOptionResponse(
    val optionKey: String,
    val name: String,
    val price: Long,
    val priceOverridden: Boolean,
)
