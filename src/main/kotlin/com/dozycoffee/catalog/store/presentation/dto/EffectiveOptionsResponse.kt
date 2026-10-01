package com.dozycoffee.catalog.store.presentation.dto

import com.dozycoffee.catalog.product.application.policy.EffectiveOptionConfig
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 유효 옵션 구성(요구사항 1.9). 본사 API의 유효 옵션 구성과 같은 형식이다(docs/api/product.md).
// presentation은 자기 모듈의 응답 타입만 쓰므로 같은 모양을 이 모듈에도 둔다. 형식을 바꾸면 두 곳을 함께 고친다.
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
