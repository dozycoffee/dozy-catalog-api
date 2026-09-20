package com.dozycoffee.catalog.infrastructure.persistence.scheduledchange

import com.dozycoffee.catalog.application.scheduledchange.OptionGroupFieldValue
import com.dozycoffee.catalog.application.scheduledchange.ProductFieldValue
import com.dozycoffee.catalog.application.scheduledchange.ScheduledFieldValue
import com.dozycoffee.catalog.common.exposed.JsonbCodec
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.product.model.OptionOverride
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.domain.scheduledchange.ScheduledValue
import com.dozycoffee.catalog.domain.tag.TagId
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

// scheduled_changes.new_value의 저장 형식. {"type": 값 종류, "value": 값}이며 값이 없는 활성화·단종은 type만 둔다.
// type은 필드 이름과 같되, 옵션 그룹별 예외는 옵션 그룹 ID를 값에 담으므로 "optionOverrides"로 고정한다.
// 형식은 docs/erd.md(scheduled_changes)에 있다. 저장된 JSON이 계속 읽혀야 하므로 type 문자열과 키 이름은 바꾸지 않는다.
object ScheduledValueJsonbCodec : JsonbCodec<ScheduledValue> {
    private val json = JsonNodeFactory.instance

    override fun toJson(value: ScheduledValue): JsonNode {
        require(value is ScheduledFieldValue) { "예약 값은 ScheduledFieldValue여야 합니다: ${value::class}" }
        val node = json.objectNode().put(TYPE, typeOf(value))
        when (value) {
            is ProductFieldValue.Name -> node.put(VALUE, value.name)
            is ProductFieldValue.Category -> node.put(VALUE, value.categoryId.value)
            is ProductFieldValue.Description -> node.put(VALUE, value.description)
            is ProductFieldValue.Image -> node.put(VALUE, value.imageUrl)
            is ProductFieldValue.BasePrice -> node.put(VALUE, value.basePrice.amount)
            is ProductFieldValue.Tags -> node.set(VALUE, longs(value.tagIds.map { it.value }.sorted()))
            is ProductFieldValue.Groups -> node.set(VALUE, longs(value.groupIds.map { it.value }.sorted()))
            is ProductFieldValue.Scope -> node.set(VALUE, storeScopeToJson(value.storeScope))
            ProductFieldValue.Activation, ProductFieldValue.Discontinuation -> Unit
            is ProductFieldValue.OptionGroupLinks -> node.set(VALUE, longs(value.optionGroupIds.map { it.value }))
            is ProductFieldValue.OptionOverrides ->
                node.set(
                    VALUE,
                    json
                        .objectNode()
                        .put("optionGroupId", value.optionGroupId.value)
                        .set("overrides", array(value.overrides.map(::overrideToJson))),
                )
            is OptionGroupFieldValue.Options -> node.set(VALUE, array(value.options.map(::optionToJson)))
        }
        return node
    }

    override fun fromJson(node: JsonNode): ScheduledFieldValue {
        val value by lazy { node.required(VALUE) }
        return when (val type = node.required(TYPE).stringValue()) {
            "name" -> ProductFieldValue.Name(value.stringValue())
            "category" -> ProductFieldValue.Category(CategoryId(value.longValue()))
            "description" -> ProductFieldValue.Description(value.nullableString())
            "image" -> ProductFieldValue.Image(value.nullableString())
            "basePrice" -> ProductFieldValue.BasePrice(Money(value.longValue()))
            "tags" -> ProductFieldValue.Tags(value.items().map { TagId(it.longValue()) }.toSet())
            "groups" -> ProductFieldValue.Groups(value.items().map { ProductGroupId(it.longValue()) }.toSet())
            "storeScope" -> ProductFieldValue.Scope(storeScopeFromJson(value))
            "activation" -> ProductFieldValue.Activation
            "discontinuation" -> ProductFieldValue.Discontinuation
            "optionGroupLinks" -> ProductFieldValue.OptionGroupLinks(value.items().map { OptionGroupId(it.longValue()) })
            "optionOverrides" ->
                ProductFieldValue.OptionOverrides(
                    optionGroupId = OptionGroupId(value.required("optionGroupId").longValue()),
                    overrides = value.required("overrides").items().map(::overrideFromJson),
                )
            "options" -> OptionGroupFieldValue.Options(value.items().map(::optionFromJson))
            else -> error("알 수 없는 예약 값 종류입니다: $type")
        }
    }

    private fun typeOf(value: ScheduledFieldValue): String =
        when (value) {
            is ProductFieldValue.OptionOverrides -> "optionOverrides"
            else -> value.fieldName
        }

    private fun storeScopeToJson(scope: StoreScope): ObjectNode =
        when (scope) {
            StoreScope.All -> json.objectNode().put("kind", "ALL")
            is StoreScope.Limited ->
                json
                    .objectNode()
                    .put("kind", "LIMITED")
                    .set("targetStoreIds", longs(scope.targetStoreIds.map { it.value }.sorted()))
        }

    private fun storeScopeFromJson(node: JsonNode): StoreScope =
        when (val kind = node.required("kind").stringValue()) {
            "ALL" -> StoreScope.All
            "LIMITED" ->
                StoreScope.Limited(
                    node
                        .required("targetStoreIds")
                        .items()
                        .map { StoreId(it.longValue()) }
                        .toSet(),
                )
            else -> error("알 수 없는 판매 범위입니다: $kind")
        }

    private fun overrideToJson(override: OptionOverride): ObjectNode =
        when (override) {
            is OptionOverride.Price ->
                json
                    .objectNode()
                    .put("optionKey", override.optionKey.value)
                    .put("type", "PRICE")
                    .put("price", override.price.amount)
            is OptionOverride.Exclude ->
                json
                    .objectNode()
                    .put("optionKey", override.optionKey.value)
                    .put("type", "EXCLUDE")
        }

    private fun overrideFromJson(node: JsonNode): OptionOverride {
        val optionKey = OptionKey(node.required("optionKey").stringValue())
        return when (val type = node.required("type").stringValue()) {
            "PRICE" -> OptionOverride.Price(optionKey, Money(node.required("price").longValue()))
            "EXCLUDE" -> OptionOverride.Exclude(optionKey)
            else -> error("알 수 없는 옵션 예외 종류입니다: $type")
        }
    }

    private fun optionToJson(option: Option): ObjectNode =
        json
            .objectNode()
            .put("optionKey", option.optionKey.value)
            .put("name", option.name)
            .put("price", option.price.amount)

    private fun optionFromJson(node: JsonNode): Option =
        Option(
            optionKey = OptionKey(node.required("optionKey").stringValue()),
            name = node.required("name").stringValue(),
            price = Money(node.required("price").longValue()),
        )

    private fun longs(values: List<Long>): ArrayNode = json.arrayNode().also { array -> values.forEach { array.add(it) } }

    private fun array(nodes: List<JsonNode>): ArrayNode = json.arrayNode().also { array -> nodes.forEach { array.add(it) } }

    private fun JsonNode.items(): Collection<JsonNode> {
        check(isArray) { "배열이어야 합니다: $this" }
        return values()
    }

    private fun JsonNode.nullableString(): String? = if (isNull) null else stringValue()

    private const val TYPE = "type"
    private const val VALUE = "value"
}
