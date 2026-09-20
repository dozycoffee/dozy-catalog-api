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
    val newValue: ScheduledFieldValue,
    val effectiveDate: LocalDate,
) {
    companion object {
        fun forProduct(
            productId: ProductId,
            newValue: ProductFieldValue,
            effectiveDate: LocalDate,
        ) = RegisterScheduledChangeCommand(productId.value, newValue, effectiveDate)

        fun forOptionGroup(
            optionGroupId: OptionGroupId,
            newValue: OptionGroupFieldValue,
            effectiveDate: LocalDate,
        ) = RegisterScheduledChangeCommand(optionGroupId.value, newValue, effectiveDate)
    }
}
