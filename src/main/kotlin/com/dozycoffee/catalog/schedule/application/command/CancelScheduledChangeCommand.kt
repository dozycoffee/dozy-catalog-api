package com.dozycoffee.catalog.schedule.application.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.schedule.domain.TargetKind

// 예약 취소(요구사항 1.4 / 시나리오 S2). 대기 예약은 대상·필드당 최대 1건이라 예약 ID 대신 대상과 필드로 가리킨다.
// 필드 이름의 원본은 값 타입이므로(ADR-0014) 호출하는 쪽은 그 타입의 fieldName을 넘긴다
// (예: ProductFieldValue.Activation.fieldName, ProductFieldValue.OptionOverrides.fieldNameOf(optionGroupId)).
data class CancelScheduledChangeCommand private constructor(
    val targetId: Long,
    val targetKind: TargetKind,
    val fieldName: String,
) {
    companion object {
        fun forProduct(
            productId: ProductId,
            fieldName: String,
        ) = CancelScheduledChangeCommand(productId.value, TargetKind.PRODUCT, fieldName)

        fun forOptionGroup(
            optionGroupId: OptionGroupId,
            fieldName: String,
        ) = CancelScheduledChangeCommand(optionGroupId.value, TargetKind.OPTION_GROUP, fieldName)
    }
}
