package com.dozycoffee.catalog.product.domain.optiongroup

import com.dozycoffee.catalog.core.VersionedAggregateRoot
import com.dozycoffee.catalog.product.domain.optiongroup.exception.DuplicateOptionKeyException
import com.dozycoffee.catalog.product.domain.optiongroup.exception.EmptyOptionGroupException

class OptionGroup internal constructor(
    id: OptionGroupId,
    name: String,
    selectionType: SelectionType,
    required: Boolean,
    options: List<Option>,
    version: Long = 0,
) : VersionedAggregateRoot<OptionGroupId>(id, version) {
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

    internal companion object {
        // 옵션 목록 교체를 연결 상품과 함께 판단하는 application 정책(OptionReplacementPolicy)이
        // 상품 검증보다 먼저 목록 자체의 유효성을 확인할 수 있도록 internal로 연다.
        // 옵션 목록 예약을 등록할 때도 같은 규칙으로 스냅샷을 확인한다(ScheduledValueValidator).
        internal fun validateOptions(options: List<Option>) {
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
