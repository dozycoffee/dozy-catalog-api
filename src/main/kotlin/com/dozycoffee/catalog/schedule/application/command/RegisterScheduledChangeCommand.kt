package com.dozycoffee.catalog.schedule.application.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.schedule.application.OptionGroupFieldValue
import com.dozycoffee.catalog.schedule.application.ProductFieldValue
import com.dozycoffee.catalog.schedule.application.ScheduledFieldValue
import java.time.LocalDate

// 예약 등록(요구사항 1.4 / 시나리오 S2). 대상 종류와 필드 이름은 값 타입이 정하므로 따로 받지 않는다(ADR-0014).
// targetId는 products.id 또는 option_groups.id를 다형 참조하는 Long이다. 대상별 팩토리로만 만들어,
// 상품 ID에 옵션 그룹 필드 값을 담는 조합을 컴파일 단계에서 막는다.
data class RegisterScheduledChangeCommand private constructor(
    val targetId: Long,
    val input: Input,
    val effectiveDate: LocalDate,
) {
    // 입력한 예약 값. 대부분은 저장할 값 그대로 받는다. 태그는 즉시 변경과 같이 이름으로 받고,
    // 등록 트랜잭션에서 태그를 찾거나 만들어 ID로 바꾼 뒤 저장한다(요구사항 1.4, 1.7).
    sealed interface Input {
        data class Value(
            val value: ScheduledFieldValue,
        ) : Input

        data class TagNames(
            val tagNames: List<String>,
        ) : Input
    }

    companion object {
        fun forProduct(
            productId: ProductId,
            newValue: ProductFieldValue,
            effectiveDate: LocalDate,
        ) = RegisterScheduledChangeCommand(productId.value, Input.Value(newValue), effectiveDate)

        // 태그 예약. 없는 이름의 태그는 등록할 때 만들어지고, 예약이 취소되어도 남는다.
        fun forProductTags(
            productId: ProductId,
            tagNames: List<String>,
            effectiveDate: LocalDate,
        ) = RegisterScheduledChangeCommand(productId.value, Input.TagNames(tagNames), effectiveDate)

        fun forOptionGroup(
            optionGroupId: OptionGroupId,
            newValue: OptionGroupFieldValue,
            effectiveDate: LocalDate,
        ) = RegisterScheduledChangeCommand(optionGroupId.value, Input.Value(newValue), effectiveDate)
    }
}
