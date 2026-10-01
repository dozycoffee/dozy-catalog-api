package com.dozycoffee.catalog.product.presentation.product.dto

import com.dozycoffee.catalog.product.application.policy.EffectiveOption
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionConfig
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 유효 옵션 구성과 표시용 시작가(docs/api/product.md effective-options, 요구사항 1.9).
// 계산은 EffectiveOptionResolver가 하고, 여기서는 그 결과를 그대로 옮긴다.
data class EffectiveOptionsResponse(
    val productId: Long,
    val basePrice: Long,
    val displayStartingPrice: Long,
    // 상품의 연결 순서다.
    val groups: List<EffectiveOptionGroupResponse>,
) {
    companion object {
        fun from(config: EffectiveOptionConfig): EffectiveOptionsResponse =
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
    // 필수 그룹에 유효 옵션이 1개면 그 옵션 키, 아니면 null.
    val autoSelectedOptionKey: String?,
    // 옵션 그룹의 순서이고, 이 상품에서 제외된 옵션은 빠진다.
    val options: List<EffectiveOptionResponse>,
) {
    companion object {
        fun from(group: EffectiveOptionGroup): EffectiveOptionGroupResponse =
            EffectiveOptionGroupResponse(
                optionGroupId = group.optionGroupId.value,
                name = group.name,
                selectionType = group.selectionType,
                required = group.required,
                autoSelectedOptionKey = group.autoSelectedOption?.optionKey?.value,
                options = group.options.map(EffectiveOptionResponse::from),
            )
    }
}

data class EffectiveOptionResponse(
    val optionKey: String,
    val name: String,
    val price: Long,
    val priceOverridden: Boolean,
) {
    companion object {
        fun from(option: EffectiveOption): EffectiveOptionResponse =
            EffectiveOptionResponse(
                optionKey = option.optionKey.value,
                name = option.name,
                price = option.price.amount,
                priceOverridden = option.priceOverridden,
            )
    }
}
