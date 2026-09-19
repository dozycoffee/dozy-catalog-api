package com.dozycoffee.catalog.domain.product.service

import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.domain.product.model.Product

// 옵션 그룹의 옵션 목록 교체를 허용할지 판단한다(요구사항 1.9). 즉시 교체와 예약 스냅샷 적용이
// 같은 규칙을 쓴다. 옵션 목록은 OptionGroup이, 제외 설정은 각 Product가 갖고 있어 연결 상품
// 전체를 함께 봐야 판단할 수 있으므로 도메인 서비스로 둔다. 판단만 하고 어떤 애그리거트도
// 바꾸지 않는다.
//
// 변경 조율과 트랜잭션 경계는 application의 몫이다. 한 트랜잭션에서
// 옵션 그룹 잠금 → 연결 상품 전체 조회 → check() → optionGroup.replaceOptions()
// → 상품마다 removeOverrides(optionGroupId, plan.removedOptionKeys) → 저장 순으로 처리한다.
object OptionReplacementPolicy {
    // linkedProducts: 이 옵션 그룹을 연결한 상품 전체(상태 무관). 빠진 상품은 검사할 수 없으므로
    // application이 누락 없이 조회해 넘겨야 한다. 연결하지 않은 상품이 섞이면 호출 코드 오류이므로
    // require로 거부한다(docs/architecture/exception.md).
    //
    // 1. 새 목록 자체의 유효성(1개 이상, 키 유일)
    // 2. 연결 상품마다 새 목록으로 선택 가능한 옵션을 계산해 0개면 NoSelectableOptionException
    // 3. 현재 목록에서 새 목록으로 오며 사라지는 옵션 키를 계산해 돌려준다
    fun check(
        optionGroup: OptionGroup,
        newOptions: List<Option>,
        linkedProducts: Collection<Product>,
    ): OptionReplacementPlan {
        OptionGroup.validateOptions(newOptions)
        linkedProducts.forEach { product ->
            val link = product.optionGroupLinks.firstOrNull { it.id == optionGroup.id }
            requireNotNull(link) {
                "옵션 그룹(${optionGroup.id.value})을 연결하지 않은 상품이 섞여 있습니다: ${product.id.value}"
            }
            if (newOptions.all { it.optionKey in link.excludedOptionKeys }) {
                throw NoSelectableOptionException(product.id, optionGroup.id)
            }
        }

        val newKeys = newOptions.map { it.optionKey }.toSet()
        return OptionReplacementPlan(
            removedOptionKeys =
                optionGroup.options
                    .map { it.optionKey }
                    .filterNot { it in newKeys }
                    .toSet(),
        )
    }
}

// removedOptionKeys: 현재 목록에는 있으나 새 목록에는 없는 옵션 키. 연결 상품의 이 키 예외는
// 교체와 함께 삭제한다(이후 같은 키가 다시 생겨도 복원하지 않음).
data class OptionReplacementPlan(
    val removedOptionKeys: Set<OptionKey>,
)
