package com.dozycoffee.catalog.domain.optiongroup

import com.dozycoffee.catalog.domain.optiongroup.exception.DuplicateOptionKeyException
import com.dozycoffee.catalog.domain.optiongroup.exception.EmptyOptionGroupException
import com.dozycoffee.catalog.domain.shared.AggregateRoot

class OptionGroup internal constructor(
    id: OptionGroupId,
    name: String,
    selectionType: SelectionType,
    required: Boolean,
    options: List<Option>,
) : AggregateRoot<OptionGroupId>(id) {
    var name: String = name
        private set
    var selectionType: SelectionType = selectionType
        private set
    var required: Boolean = required
        private set
    var options: List<Option> = options
        private set

    fun rename(newName: String) {
        this.name = newName
    }

    fun changeSelectionType(newSelectionType: SelectionType) {
        this.selectionType = newSelectionType
    }

    fun changeRequired(newRequired: Boolean) {
        this.required = newRequired
    }

    fun replaceOptions(newOptions: List<Option>) {
        validateOptions(newOptions)
        this.options = newOptions
    }

    class NewOptionGroup private constructor(
        val name: String,
        val selectionType: SelectionType,
        val required: Boolean,
        val options: List<Option>,
    ) {
        companion object {
            // 검증을 거치지 않고는 인스턴스를 만들 수 없는 유일한 생성 경로
            fun of(
                name: String,
                selectionType: SelectionType,
                required: Boolean,
                options: List<Option>,
            ): NewOptionGroup {
                validateOptions(options)
                return NewOptionGroup(name, selectionType, required, options)
            }
        }
    }

    private companion object {
        fun validateOptions(options: List<Option>) {
            if (options.isEmpty()) {
                throw EmptyOptionGroupException()
            }
            val duplicateKey =
                options
                    .groupBy { it.optionKey }
                    .entries
                    .firstOrNull { it.value.size > 1 }
                    ?.key
            if (duplicateKey != null) {
                throw DuplicateOptionKeyException(duplicateKey)
            }
        }
    }
}
