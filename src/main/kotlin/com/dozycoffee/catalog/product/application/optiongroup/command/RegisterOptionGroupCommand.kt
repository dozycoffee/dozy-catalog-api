package com.dozycoffee.catalog.product.application.optiongroup.command

import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 옵션 그룹 생성 입력(요구사항 1.9). 옵션 목록의 순서가 곧 노출 순서다.
// 옵션이 0개이거나 옵션 키가 겹치면 OptionGroup이 거부한다.
data class RegisterOptionGroupCommand(
    val name: String,
    val selectionType: SelectionType,
    val required: Boolean,
    val options: List<Option>,
)
