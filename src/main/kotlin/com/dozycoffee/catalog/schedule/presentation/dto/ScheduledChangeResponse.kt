package com.dozycoffee.catalog.schedule.presentation.dto

import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.schedule.application.OptionGroupFieldValue
import com.dozycoffee.catalog.schedule.application.ProductFieldValue
import com.dozycoffee.catalog.schedule.application.ScheduledFieldValue
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.presentation.ProductScheduleField
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.time.LocalDate

// 예약(docs/api/schedule.md ScheduledChange). 예약 ID는 드러내지 않고 필드 이름으로 가리킨다.
// field는 경로에 쓰는 이름(option-overrides/7)이고, value는 요청과 같은 형식이되 태그만 ID 배열이다.
data class ScheduledChangeResponse(
    val field: String,
    val value: JsonNode?,
    val effectiveDate: LocalDate,
    val status: ScheduleStatus,
) {
    companion object {
        private val json = JsonNodeFactory.instance

        fun from(scheduledChange: ScheduledChange): ScheduledChangeResponse {
            val newValue = scheduledChange.newValue
            // 저장된 예약 값은 언제나 이 sealed 계층이다. 아니라면 등록·직렬화 경로의 코드 오류다(docs/architecture/exception.md).
            check(newValue is ScheduledFieldValue) {
                "알 수 없는 예약 값입니다: 예약 ${scheduledChange.id.value}, ${newValue::class.simpleName}"
            }
            return ScheduledChangeResponse(
                field = fieldOf(newValue),
                value = valueOf(newValue),
                effectiveDate = scheduledChange.effectiveDate,
                status = scheduledChange.status,
            )
        }

        private fun fieldOf(value: ScheduledFieldValue): String =
            when (value) {
                is ProductFieldValue.OptionOverrides -> ProductScheduleField.OptionOverrides.pathOf(value.optionGroupId)
                else -> value.fieldName
            }

        // else 없이 모든 값 타입을 나열해, 예약 가능한 필드를 추가하고 응답 형식을 빠뜨리면 컴파일 에러가 되게 한다(ADR-0014).
        private fun valueOf(value: ScheduledFieldValue): JsonNode? =
            when (value) {
                is ProductFieldValue.Name -> json.stringNode(value.name)
                is ProductFieldValue.Category -> json.numberNode(value.categoryId.value)
                is ProductFieldValue.Description -> value.description?.let(json::stringNode)
                is ProductFieldValue.Image -> value.imageUrl?.let(json::stringNode)
                is ProductFieldValue.BasePrice -> json.numberNode(value.basePrice.amount)
                is ProductFieldValue.Tags -> longs(value.tagIds.map { it.value }.sorted())
                is ProductFieldValue.Groups -> longs(value.groupIds.map { it.value }.sorted())
                is ProductFieldValue.Scope -> storeScope(value.storeScope)
                ProductFieldValue.Activation, ProductFieldValue.Discontinuation -> null
                is ProductFieldValue.OptionGroupLinks -> longs(value.optionGroupIds.map { it.value })
                is ProductFieldValue.OptionOverrides -> array(value.overrides.map(::optionOverride))
                is OptionGroupFieldValue.Options -> array(value.options.map(::option))
            }

        // 상품 API의 판매 범위와 같은 형식이다. ALL이면 targetStoreIds는 빈 배열이다(docs/api/product.md).
        private fun storeScope(scope: StoreScope): ObjectNode =
            when (scope) {
                StoreScope.All -> json.objectNode().put("kind", "ALL").set("targetStoreIds", json.arrayNode())
                is StoreScope.Limited ->
                    json
                        .objectNode()
                        .put("kind", "LIMITED")
                        .set("targetStoreIds", longs(scope.targetStoreIds.map { it.value }.sorted()))
            }

        // 상품 API의 예외와 같은 형식이다. EXCLUDE면 price가 null이다(docs/api/product.md).
        private fun optionOverride(override: OptionOverride): ObjectNode {
            val node = json.objectNode().put("optionKey", override.optionKey.value)
            return when (override) {
                is OptionOverride.Price -> node.put("type", "PRICE").put("price", override.price.amount)
                is OptionOverride.Exclude -> node.put("type", "EXCLUDE").putNull("price")
            }
        }

        private fun option(option: Option): ObjectNode =
            json
                .objectNode()
                .put("optionKey", option.optionKey.value)
                .put("name", option.name)
                .put("price", option.price.amount)

        private fun longs(values: List<Long>): ArrayNode = json.arrayNode().also { array -> values.forEach { array.add(it) } }

        private fun array(nodes: List<JsonNode>): ArrayNode = json.arrayNode().also { array -> nodes.forEach { array.add(it) } }
    }
}
