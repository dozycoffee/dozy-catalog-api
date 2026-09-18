package com.dozycoffee.catalog.domain.product.service

import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.product.exception.LinkedOptionGroupNotFoundException
import com.dozycoffee.catalog.domain.product.exception.ProductOptionGroupNotLinkedException
import com.dozycoffee.catalog.domain.product.model.OptionOverride
import com.dozycoffee.catalog.domain.product.model.Product

// 상품의 유효 옵션 구성을 계산한다. Product(연결·예외)와 OptionGroup(옵션 목록)을 함께 봐야 해서
// 어느 한 애그리거트에 두지 않고 도메인 서비스로 분리했다. 조회뿐 아니라 옵션 규칙 검증
// (제외·옵션 목록 교체로 선택 가능한 옵션이 0개가 되는지)도 같은 계산 위에서 판단한다.
object EffectiveOptionResolver {
    // optionGroups는 상품에 연결된 옵션 그룹 전체와 정확히 일치해야 한다.
    // 연결되지 않은 그룹이 섞이거나 연결된 그룹이 빠지면 거부한다.
    fun resolve(
        product: Product,
        optionGroups: Collection<OptionGroup>,
    ): EffectiveOptionConfig {
        val optionGroupsById = optionGroups.associateBy { it.id }
        val linkedIds = product.optionGroupLinks.map { it.id }.toSet()
        optionGroupsById.keys.firstOrNull { it !in linkedIds }?.let {
            throw ProductOptionGroupNotLinkedException(product.id, it)
        }
        val missingIds = linkedIds - optionGroupsById.keys
        if (missingIds.isNotEmpty()) {
            throw LinkedOptionGroupNotFoundException(product.id, missingIds)
        }

        val groups =
            product.optionGroupLinks
                .sortedBy { it.displayOrder }
                .map { link -> resolveGroup(optionGroupsById.getValue(link.id), link.overrides) }
        return EffectiveOptionConfig(product.id, product.basePrice, groups)
    }

    // options를 따로 받는 이유: 옵션 목록 교체 검증에서는 아직 반영하지 않은 새 목록으로,
    // 제외 검증에서는 아직 저장하지 않은 예외 목록으로 계산해 봐야 하기 때문이다.
    internal fun resolveGroup(
        optionGroup: OptionGroup,
        overrides: List<OptionOverride>,
        options: List<Option> = optionGroup.options,
    ): EffectiveOptionGroup {
        val overridesByKey = overrides.associateBy { it.optionKey }
        val effectiveOptions =
            options.mapNotNull { option ->
                when (val override = overridesByKey[option.optionKey]) {
                    is OptionOverride.Exclude -> null
                    is OptionOverride.Price -> EffectiveOption(option.optionKey, option.name, override.price, priceOverridden = true)
                    null -> EffectiveOption(option.optionKey, option.name, option.price, priceOverridden = false)
                }
            }
        return EffectiveOptionGroup(
            optionGroupId = optionGroup.id,
            name = optionGroup.name,
            selectionType = optionGroup.selectionType,
            required = optionGroup.required,
            options = effectiveOptions,
        )
    }
}
