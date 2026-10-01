package com.dozycoffee.catalog.product.presentation.product.dto

import com.dozycoffee.catalog.common.web.InvalidRequestException
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.product.command.ChangeStoreScopeCommand
import com.dozycoffee.catalog.product.application.product.command.LinkOptionGroupCommand
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.application.product.command.ReorderOptionGroupsCommand
import com.dozycoffee.catalog.product.application.product.command.ReplaceProductCommand
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId

// 상품 API의 요청 본문(docs/api/product.md). Command로 바꿀 때 도메인 타입으로 감싼다.
// 금액은 여기서 Money로 바꾸므로 음수면 InvalidMoneyAmountException(400 INVALID_MONEY_AMOUNT)이 된다.
// 목록 필드는 비었으면 []로 보내지만, 선택 필드라 생략하거나 null로 보내도 빈 목록으로 읽는다.

// POST /products. 배열 순서가 옵션 그룹의 노출 순서다.
data class RegisterProductRequest(
    val name: String,
    val categoryId: Long,
    val basePrice: Long,
    val tracksInventory: Boolean,
    val description: String? = null,
    val imageUrl: String? = null,
    val tagNames: List<String>? = null,
    val groupIds: List<Long>? = null,
    val optionGroupIds: List<Long>? = null,
) {
    fun toCommand(): RegisterProductCommand =
        RegisterProductCommand(
            name = name,
            categoryId = CategoryId(categoryId),
            basePrice = Money(basePrice),
            tracksInventory = tracksInventory,
            description = description,
            imageUrl = imageUrl,
            tagNames = tagNames.orEmpty(),
            groupIds = groupIds.orEmpty().map(::ProductGroupId).toSet(),
            optionGroupIds = optionGroupIds.orEmpty().map(::OptionGroupId),
        )
}

// PUT /products/{productId}. 등록 본문에서 재고 추적 여부와 옵션 그룹 연결을 뺀 것이다(각각 바꿀 수 없거나 전용 엔드포인트).
data class ReplaceProductRequest(
    val name: String,
    val categoryId: Long,
    val basePrice: Long,
    val description: String? = null,
    val imageUrl: String? = null,
    val tagNames: List<String>? = null,
    val groupIds: List<Long>? = null,
) {
    fun toCommand(
        productId: ProductId,
        version: Long,
    ): ReplaceProductCommand =
        ReplaceProductCommand(
            productId = productId,
            version = version,
            name = name,
            categoryId = CategoryId(categoryId),
            basePrice = Money(basePrice),
            description = description,
            imageUrl = imageUrl,
            tagNames = tagNames.orEmpty(),
            groupIds = groupIds.orEmpty().map(::ProductGroupId).toSet(),
        )
}

// PUT /products/{productId}/store-scope. ALL이면 대상 매장을 비워야 하고, LIMITED는 빈 목록도 허용한다(요구사항 1.5).
data class ChangeStoreScopeRequest(
    val kind: StoreScopeKind,
    val targetStoreIds: List<Long>? = null,
) {
    fun toCommand(
        productId: ProductId,
        version: Long,
    ): ChangeStoreScopeCommand = ChangeStoreScopeCommand(productId, version, toScope())

    private fun toScope(): StoreScope {
        val storeIds = targetStoreIds.orEmpty()
        return when (kind) {
            StoreScopeKind.ALL -> {
                if (storeIds.isNotEmpty()) throw InvalidRequestException("kind가 ALL이면 targetStoreIds는 비워야 합니다")
                StoreScope.All
            }
            StoreScopeKind.LIMITED -> StoreScope.Limited(storeIds.map(::StoreId).toSet())
        }
    }
}

// POST /products/{productId}/option-groups. 기존 연결들 뒤에 붙는다.
data class LinkOptionGroupRequest(
    val optionGroupId: Long,
) {
    fun toCommand(
        productId: ProductId,
        version: Long,
    ): LinkOptionGroupCommand = LinkOptionGroupCommand(productId, version, OptionGroupId(optionGroupId))
}

// PUT /products/{productId}/option-groups/order. 연결된 옵션 그룹 전체를 정확히 한 번씩 담아야 한다(Product가 확인한다).
data class ReorderOptionGroupsRequest(
    val optionGroupIds: List<Long>,
) {
    fun toCommand(
        productId: ProductId,
        version: Long,
    ): ReorderOptionGroupsCommand = ReorderOptionGroupsCommand(productId, version, optionGroupIds.map(::OptionGroupId))
}

// PUT /products/{productId}/option-groups/{optionGroupId}/overrides/{optionKey}.
// PRICE면 price가 있어야 하고, EXCLUDE면 price를 보내지 않는다. 종류마다 유스케이스가 다르므로 컨트롤러가 결과 타입으로 나눈다.
data class SetOptionOverrideRequest(
    val type: OptionOverrideType,
    val price: Long? = null,
) {
    fun toOverride(optionKey: OptionKey): OptionOverride =
        when (type) {
            OptionOverrideType.PRICE ->
                OptionOverride.Price(optionKey, Money(price ?: throw InvalidRequestException("type이 PRICE면 price가 있어야 합니다")))
            OptionOverrideType.EXCLUDE -> {
                if (price != null) throw InvalidRequestException("type이 EXCLUDE면 price를 보내지 않습니다")
                OptionOverride.Exclude(optionKey)
            }
        }
}
