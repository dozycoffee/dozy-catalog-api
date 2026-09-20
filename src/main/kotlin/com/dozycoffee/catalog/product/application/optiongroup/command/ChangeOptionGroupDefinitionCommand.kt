package com.dozycoffee.catalog.product.application.optiongroup.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 이름·선택 방식·필수 여부는 즉시 반영만 한다(요구사항 1.9). 예약할 수 없는 필드다(요구사항 1.4).
// version은 화면이 보고 있던 버전이다(ADR-0013). 옵션 목록은 ReplaceOptionsCommand로 바꾼다.
data class ChangeOptionGroupDefinitionCommand(
    val optionGroupId: OptionGroupId,
    val version: Long,
    val name: String,
    val selectionType: SelectionType,
    val required: Boolean,
)
