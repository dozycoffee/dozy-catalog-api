package com.dozycoffee.catalog.domain.product.service

import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.domain.product.exception.ProductOptionGroupNotLinkedException
import com.dozycoffee.catalog.domain.product.model.Product

// 옵션 그룹의 옵션 목록 교체(요구사항 1.9). 즉시 교체와 예약 스냅샷 적용이 같은 로직을 쓴다.
// 옵션 목록은 OptionGroup이, 제외 설정은 각 Product가 갖고 있어 연결 상품 전체를 함께 봐야
// 판단할 수 있으므로 도메인 서비스로 둔다. 저장소를 모르는 순수 계산이며, 연결 상품 조회·잠금과
// 저장은 application의 몫이다.
object OptionListReplacer {
    // linkedProducts: 이 옵션 그룹을 연결한 상품 전체(상태 무관). 빠진 상품은 검사할 수 없으므로
    // application이 누락 없이 조회해 넘겨야 한다. 연결하지 않은 상품이 섞이면 거부한다.
    //
    // 모든 검증을 통과한 뒤에만 옵션 그룹과 상품을 바꾼다. 거부되면 아무것도 바뀌지 않는다.
    // 1. 새 목록 자체의 유효성(1개 이상, 키 유일)
    // 2. 연결 상품마다 새 목록으로 유효 구성을 계산해 빈 그룹이 생기면 NoSelectableOptionException
    // 3. 옵션 목록 교체
    // 4. 새 목록에서 사라진 옵션 키의 상품별 예외 삭제 (이후 같은 키가 다시 생겨도 복원하지 않음)
    fun replace(
        optionGroup: OptionGroup,
        newOptions: List<Option>,
        linkedProducts: Collection<Product>,
    ) {
        OptionGroup.validateOptions(newOptions)
        linkedProducts.forEach { product ->
            val link =
                product.optionGroupLinks.firstOrNull { it.id == optionGroup.id }
                    ?: throw ProductOptionGroupNotLinkedException(product.id, optionGroup.id)
            val effective = EffectiveOptionResolver.resolveGroup(optionGroup, link.overrides, newOptions)
            if (effective.options.isEmpty()) {
                throw NoSelectableOptionException(product.id, optionGroup.id)
            }
        }

        optionGroup.replaceOptions(newOptions)
        linkedProducts.forEach { it.removeOverridesOfMissingOptions(optionGroup) }
    }
}
