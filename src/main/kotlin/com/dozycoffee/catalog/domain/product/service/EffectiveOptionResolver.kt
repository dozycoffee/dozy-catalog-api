package com.dozycoffee.catalog.domain.product.service

import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.product.model.OptionOverride
import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductOptionGroupLink

// 상품의 유효 옵션 구성을 계산한다. Product(연결·예외)와 OptionGroup(옵션 목록)을 함께 봐야 해서
// 어느 한 애그리거트에 두지 않고 도메인 서비스로 분리했다. 조회뿐 아니라 옵션 규칙 검증
// (제외·옵션 목록 교체로 선택 가능한 옵션이 0개가 되는지)도 같은 계산 위에서 판단한다.
object EffectiveOptionResolver {
    // optionGroups는 상품에 연결된 옵션 그룹 전체와 정확히 일치해야 한다. 어긋나면 application이
    // 옵션 그룹을 잘못 불러온 호출 코드 오류이므로 DomainException이 아닌 require로 거부한다
    // (docs/architecture/exception.md).
    fun resolve(
        product: Product,
        optionGroups: Collection<OptionGroup>,
    ): EffectiveOptionConfig {
        val optionGroupsById = optionGroups.associateBy { it.id }
        val linkedIds = product.optionGroupLinks.map { it.id }.toSet()
        require(optionGroupsById.keys == linkedIds) {
            "상품(${product.id.value})에 연결된 옵션 그룹과 넘긴 옵션 그룹이 다릅니다: " +
                "연결 ${linkedIds.map { it.value }}, 전달 ${optionGroupsById.keys.map { it.value }}"
        }

        val groups =
            product.optionGroupLinks
                .sortedBy { it.displayOrder }
                .map { link -> resolveGroup(optionGroupsById.getValue(link.id), link) }
        return EffectiveOptionConfig(product.id, product.basePrice, groups)
    }

    private fun resolveGroup(
        optionGroup: OptionGroup,
        link: ProductOptionGroupLink,
    ): EffectiveOptionGroup {
        val priceOverrides = link.overrides.filterIsInstance<OptionOverride.Price>().associateBy { it.optionKey }
        val effectiveOptions =
            optionGroup.options.filterNot { it.optionKey in link.excludedOptionKeys }.map { option ->
                when (val override = priceOverrides[option.optionKey]) {
                    null -> EffectiveOption(option.optionKey, option.name, option.price, priceOverridden = false)
                    else -> EffectiveOption(option.optionKey, option.name, override.price, priceOverridden = true)
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
