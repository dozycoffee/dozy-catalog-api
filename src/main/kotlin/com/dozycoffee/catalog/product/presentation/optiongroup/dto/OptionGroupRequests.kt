package com.dozycoffee.catalog.product.presentation.optiongroup.dto

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.application.optiongroup.command.ChangeOptionGroupDefinitionCommand
import com.dozycoffee.catalog.product.application.optiongroup.command.RegisterOptionGroupCommand
import com.dozycoffee.catalog.product.application.optiongroup.command.ReplaceOptionsCommand
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType

// 옵션 하나. 배열 순서가 노출 순서다(docs/api/product.md 옵션 그룹).
// 음수 가격은 Money가 INVALID_MONEY_AMOUNT(400)로 거부한다.
data class OptionRequest(
    val optionKey: String,
    val name: String,
    val price: Long,
) {
    fun toOption(): Option = Option(OptionKey(optionKey), name, Money(price))
}

data class RegisterOptionGroupRequest(
    val name: String,
    val selectionType: SelectionType,
    val required: Boolean,
    val options: List<OptionRequest>,
) {
    fun toCommand(): RegisterOptionGroupCommand =
        RegisterOptionGroupCommand(
            name = name,
            selectionType = selectionType,
            required = required,
            options = options.map { it.toOption() },
        )
}

data class ChangeOptionGroupDefinitionRequest(
    val name: String,
    val selectionType: SelectionType,
    val required: Boolean,
) {
    fun toCommand(
        optionGroupId: OptionGroupId,
        version: Long,
    ): ChangeOptionGroupDefinitionCommand =
        ChangeOptionGroupDefinitionCommand(
            optionGroupId = optionGroupId,
            version = version,
            name = name,
            selectionType = selectionType,
            required = required,
        )
}

data class ReplaceOptionsRequest(
    val options: List<OptionRequest>,
) {
    fun toCommand(
        optionGroupId: OptionGroupId,
        version: Long,
    ): ReplaceOptionsCommand =
        ReplaceOptionsCommand(
            optionGroupId = optionGroupId,
            version = version,
            options = options.map { it.toOption() },
        )
}
