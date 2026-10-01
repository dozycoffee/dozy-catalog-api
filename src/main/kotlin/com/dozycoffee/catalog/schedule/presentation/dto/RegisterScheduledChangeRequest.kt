package com.dozycoffee.catalog.schedule.presentation.dto

import com.dozycoffee.catalog.common.web.InvalidRequestException
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.schedule.application.OptionGroupFieldValue
import com.dozycoffee.catalog.schedule.application.ProductFieldValue
import com.dozycoffee.catalog.schedule.application.command.RegisterScheduledChangeCommand
import com.dozycoffee.catalog.schedule.presentation.OptionGroupScheduleField
import com.dozycoffee.catalog.schedule.presentation.ProductScheduleField
import tools.jackson.databind.JsonNode
import java.time.LocalDate

// PUT …/scheduled-changes/{field}의 본문(docs/api/schedule.md). value의 형식은 필드마다 달라 경로의 필드를 보고 읽는다.
// 필드를 else 없는 when으로 나눠, 예약 가능한 필드를 추가하고 요청 형식을 빠뜨리면 컴파일 에러가 된다(ADR-0014).
data class RegisterScheduledChangeRequest(
    val effectiveDate: LocalDate,
    val value: JsonNode? = null,
) {
    fun toCommand(
        productId: ProductId,
        field: ProductScheduleField,
    ): RegisterScheduledChangeCommand {
        val reader = JsonValueReader(value, VALUE)
        val newValue: ProductFieldValue =
            when (field) {
                is ProductScheduleField.OptionOverrides ->
                    ProductFieldValue.OptionOverrides(field.optionGroupId, reader.items().map(::optionOverride))
                is ProductScheduleField.Simple ->
                    when (field) {
                        ProductScheduleField.Simple.NAME -> ProductFieldValue.Name(reader.string())
                        ProductScheduleField.Simple.CATEGORY -> ProductFieldValue.Category(CategoryId(reader.long()))
                        ProductScheduleField.Simple.DESCRIPTION -> ProductFieldValue.Description(reader.nullableString())
                        ProductScheduleField.Simple.IMAGE -> ProductFieldValue.Image(reader.nullableString())
                        ProductScheduleField.Simple.BASE_PRICE -> ProductFieldValue.BasePrice(Money(reader.long()))
                        // 태그는 즉시 변경과 같이 이름으로 받는다. 등록할 때 태그를 찾거나 만들어 ID로 저장한다(요구사항 1.4, 1.7).
                        ProductScheduleField.Simple.TAGS ->
                            return RegisterScheduledChangeCommand.forProductTags(
                                productId,
                                reader.items().map { it.string() },
                                effectiveDate,
                            )
                        ProductScheduleField.Simple.GROUPS ->
                            ProductFieldValue.Groups(reader.items().map { ProductGroupId(it.long()) }.toSet())
                        ProductScheduleField.Simple.STORE_SCOPE -> ProductFieldValue.Scope(storeScope(reader))
                        ProductScheduleField.Simple.ACTIVATION -> ProductFieldValue.Activation.also { reader.requireNull() }
                        ProductScheduleField.Simple.DISCONTINUATION -> ProductFieldValue.Discontinuation.also { reader.requireNull() }
                        ProductScheduleField.Simple.OPTION_GROUP_LINKS ->
                            ProductFieldValue.OptionGroupLinks(reader.items().map { OptionGroupId(it.long()) })
                    }
            }
        return RegisterScheduledChangeCommand.forProduct(productId, newValue, effectiveDate)
    }

    fun toCommand(
        optionGroupId: OptionGroupId,
        field: OptionGroupScheduleField,
    ): RegisterScheduledChangeCommand {
        val reader = JsonValueReader(value, VALUE)
        val newValue: OptionGroupFieldValue =
            when (field) {
                OptionGroupScheduleField.OPTIONS -> OptionGroupFieldValue.Options(reader.items().map(::option))
            }
        return RegisterScheduledChangeCommand.forOptionGroup(optionGroupId, newValue, effectiveDate)
    }

    private fun storeScope(reader: JsonValueReader): StoreScope {
        val kind = reader.field("kind")
        val targetStoreIds = reader.field("targetStoreIds")
        return when (kind.string()) {
            // 전체 판매에 대상 매장을 함께 보내면 모순이라 거부한다(docs/api/product.md 판매 범위와 같다).
            "ALL" -> {
                if (!targetStoreIds.isNull && targetStoreIds.items().isNotEmpty()) {
                    throw InvalidRequestException("판매 범위가 ALL이면 targetStoreIds는 비워야 합니다")
                }
                StoreScope.All
            }
            "LIMITED" -> StoreScope.Limited(targetStoreIds.items().map { StoreId(it.long()) }.toSet())
            else -> kind.invalid()
        }
    }

    private fun optionOverride(item: JsonValueReader): OptionOverride {
        val optionKey = OptionKey(item.field("optionKey").string())
        val type = item.field("type")
        val price = item.field("price")
        return when (type.string()) {
            "PRICE" -> OptionOverride.Price(optionKey, Money(price.long()))
            // 제외에는 가격이 없다. 가격을 함께 보내면 무엇을 뜻하는지 알 수 없어 거부한다.
            "EXCLUDE" -> OptionOverride.Exclude(optionKey).also { price.requireNull() }
            else -> type.invalid()
        }
    }

    private fun option(item: JsonValueReader): Option =
        Option(
            optionKey = OptionKey(item.field("optionKey").string()),
            name = item.field("name").string(),
            price = Money(item.field("price").long()),
        )

    private companion object {
        const val VALUE = "value"
    }
}
