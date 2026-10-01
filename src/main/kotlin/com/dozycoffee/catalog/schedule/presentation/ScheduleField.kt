package com.dozycoffee.catalog.schedule.presentation

import com.dozycoffee.catalog.common.web.InvalidRequestException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.schedule.application.OptionGroupFieldValue
import com.dozycoffee.catalog.schedule.application.ProductFieldValue

// 경로의 {field}가 가리키는 예약 필드(docs/api/schedule.md 필드와 값). 경로의 필드 이름은 저장된 필드 이름
// (ScheduledValue.fieldName)과 같고, 옵션 그룹별 예외만 경로에서 두 단계(option-overrides/7)이고 저장할 때는
// optionOverrides:7이다. 저장된 필드 이름은 값 타입의 FIELD_NAME에서 가져와 원본을 하나로 둔다(ADR-0014).
sealed interface ProductScheduleField {
    // API 경로와 응답의 field
    val path: String

    // 대기 예약을 찾을 때 쓰는 저장된 필드 이름
    val fieldName: String

    enum class Simple(
        override val fieldName: String,
    ) : ProductScheduleField {
        NAME(ProductFieldValue.Name.FIELD_NAME),
        CATEGORY(ProductFieldValue.Category.FIELD_NAME),
        DESCRIPTION(ProductFieldValue.Description.FIELD_NAME),
        IMAGE(ProductFieldValue.Image.FIELD_NAME),
        BASE_PRICE(ProductFieldValue.BasePrice.FIELD_NAME),
        TAGS(ProductFieldValue.Tags.FIELD_NAME),
        GROUPS(ProductFieldValue.Groups.FIELD_NAME),
        STORE_SCOPE(ProductFieldValue.Scope.FIELD_NAME),
        ACTIVATION(ProductFieldValue.Activation.fieldName),
        DISCONTINUATION(ProductFieldValue.Discontinuation.fieldName),
        OPTION_GROUP_LINKS(ProductFieldValue.OptionGroupLinks.FIELD_NAME),
        ;

        override val path: String get() = fieldName
    }

    data class OptionOverrides(
        val optionGroupId: OptionGroupId,
    ) : ProductScheduleField {
        override val path: String get() = pathOf(optionGroupId)
        override val fieldName: String get() = ProductFieldValue.OptionOverrides.fieldNameOf(optionGroupId)

        companion object {
            const val PATH_PREFIX = "option-overrides/"

            fun pathOf(optionGroupId: OptionGroupId): String = "$PATH_PREFIX${optionGroupId.value}"
        }
    }

    companion object {
        private val simpleByPath = Simple.entries.associateBy { it.path }

        // 컨트롤러가 경로의 나머지({*field}, 앞의 /는 떼고)를 넘긴다. 목록에 없으면 404 UNKNOWN_SCHEDULE_FIELD다.
        // 옵션 그룹 ID가 숫자가 아니면 경로 변수의 타입이 틀린 것과 같이 400 INVALID_REQUEST다(docs/api/README.md).
        fun parse(path: String): ProductScheduleField {
            simpleByPath[path]?.let { return it }
            if (path.startsWith(OptionOverrides.PATH_PREFIX)) {
                val raw = path.removePrefix(OptionOverrides.PATH_PREFIX)
                if (raw.isNotEmpty() && '/' !in raw) {
                    val id =
                        raw.toLongOrNull()?.takeIf { it > 0 }
                            ?: throw InvalidRequestException("옵션 그룹 ID가 아닌 값입니다: $raw")
                    return OptionOverrides(OptionGroupId(id))
                }
            }
            throw UnknownScheduleFieldException(path)
        }
    }
}

// 옵션 그룹의 예약 필드. 이름·선택 방식·필수 여부는 즉시 반영만 하므로 옵션 목록 하나뿐이다.
enum class OptionGroupScheduleField(
    val fieldName: String,
) {
    OPTIONS(OptionGroupFieldValue.Options.FIELD_NAME),
    ;

    val path: String get() = fieldName

    companion object {
        fun parse(path: String): OptionGroupScheduleField =
            entries.firstOrNull { it.path == path } ?: throw UnknownScheduleFieldException(path)
    }
}
