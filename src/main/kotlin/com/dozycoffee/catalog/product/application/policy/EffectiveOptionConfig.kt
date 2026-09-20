package com.dozycoffee.catalog.product.application.policy

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.product.domain.product.ProductId

// 상품의 유효 옵션 구성: 연결된 옵션 그룹에 이 상품의 예외(제외·가격)를 반영한 결과.
// Catalog는 여기까지(가격 데이터, 유효 구성, 표시용 시작가)만 제공한다. 손님이 고른 조합의
// 금액 계산과 선택 검증(필수 누락, 단일 선택 위반 등)은 주문·POS의 책임이다.
data class EffectiveOptionConfig(
    val productId: ProductId,
    val basePrice: Money,
    // 상품의 옵션 그룹 연결 순서(displayOrder)대로 나열한다.
    val groups: List<EffectiveOptionGroup>,
) {
    // 표시용 시작가: 기준가 + 필수 그룹마다 유효 옵션 중 최저가. 선택 그룹은 더하지 않는다.
    // 자동 선택 그룹은 유효 옵션이 1개뿐이라 최저가가 곧 자동 선택 옵션의 가격이다.
    val displayStartingPrice: Money
        get() =
            groups
                .filter { it.required }
                .mapNotNull { it.minimumPrice }
                .fold(basePrice) { acc, price -> acc + price }
}

data class EffectiveOptionGroup(
    val optionGroupId: OptionGroupId,
    val name: String,
    val selectionType: SelectionType,
    val required: Boolean,
    // 옵션 그룹의 옵션 순서를 유지하고, 이 상품에서 제외된 옵션은 뺀다.
    val options: List<EffectiveOption>,
) {
    // 필수 그룹에 유효 옵션이 정확히 1개면 그 옵션이 자동으로 선택된 것으로 본다(요구사항 1.9).
    val autoSelectedOption: EffectiveOption?
        get() = options.singleOrNull()?.takeIf { required }

    // 유효 옵션이 없으면 null. 옵션 규칙 검증으로 빈 그룹이 생기지 않게 막으므로 정상 데이터에서는 항상 값이 있다.
    val minimumPrice: Money?
        get() = options.minByOrNull { it.price.amount }?.price
}

data class EffectiveOption(
    val optionKey: OptionKey,
    val name: String,
    // 이 상품에 가격 예외가 있으면 예외 가격, 없으면 옵션 그룹의 가격.
    val price: Money,
    val priceOverridden: Boolean,
)
