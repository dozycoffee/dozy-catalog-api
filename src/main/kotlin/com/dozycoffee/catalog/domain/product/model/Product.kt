package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.product.event.ProductActivated
import com.dozycoffee.catalog.domain.product.event.ProductDiscontinued
import com.dozycoffee.catalog.domain.product.event.ProductStoreScopeChanged
import com.dozycoffee.catalog.domain.product.exception.DuplicateOptionGroupLinkException
import com.dozycoffee.catalog.domain.product.exception.InvalidOptionGroupOrderException
import com.dozycoffee.catalog.domain.product.exception.InvalidProductStatusTransitionException
import com.dozycoffee.catalog.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.domain.product.exception.OptionKeyNotFoundException
import com.dozycoffee.catalog.domain.product.exception.ProductNotDeletableException
import com.dozycoffee.catalog.domain.product.exception.ProductOptionGroupNotLinkedException
import com.dozycoffee.catalog.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.domain.shared.AggregateRoot
import com.dozycoffee.catalog.domain.shared.Money
import com.dozycoffee.catalog.domain.tag.TagId

class Product internal constructor(
    id: ProductId,
    sku: Sku?,
    name: String,
    categoryId: CategoryId,
    description: String?,
    imageUrl: String?,
    basePrice: Money,
    val tracksInventory: Boolean,
    tagIds: Set<TagId> = emptySet(),
    groupIds: Set<ProductGroupId> = emptySet(),
    optionGroupLinks: List<ProductOptionGroupLink> = emptyList(),
    status: ProductStatus = ProductStatus.DRAFT,
    storeScope: StoreScope = StoreScope.All,
) : AggregateRoot<ProductId>(id) {
    var sku: Sku? = sku
        private set
    var name: String = name
        private set
    var categoryId: CategoryId = categoryId
        private set
    var description: String? = description
        private set
    var imageUrl: String? = imageUrl
        private set
    var basePrice: Money = basePrice
        private set
    var tagIds: Set<TagId> = tagIds
        private set
    var groupIds: Set<ProductGroupId> = groupIds
        private set
    var optionGroupLinks: List<ProductOptionGroupLink> = optionGroupLinks
        private set
    var status: ProductStatus = status
        private set
    var storeScope: StoreScope = storeScope
        private set

    fun rename(newName: String) {
        this.name = newName
    }

    // 소분류만 지정할 수 있다. 소분류인지는 application이 Category.requireChild()로 확인한 뒤
    // ID만 넘긴다(애그리거트끼리는 ID로만 참조, ADR-0012).
    fun changeCategory(newCategoryId: CategoryId) {
        this.categoryId = newCategoryId
    }

    fun changeDescription(newDescription: String?) {
        this.description = newDescription
    }

    fun changeImage(newImageUrl: String?) {
        this.imageUrl = newImageUrl
    }

    fun changeBasePrice(newBasePrice: Money) {
        this.basePrice = newBasePrice
    }

    fun changeTags(newTagIds: Set<TagId>) {
        this.tagIds = newTagIds
    }

    fun changeGroups(newGroupIds: Set<ProductGroupId>) {
        this.groupIds = newGroupIds
    }

    // 어떤 매장이 새로 제외됐는지는 domain이 전체 매장 목록을 알지 못해 계산할 수
    // 없다 — 구독 측(application)이 기존 StoreDisplaySetting·StoreProductAvailability(OWNER 출처)와
    // newScope를 대조해 판단하도록 이벤트에는 새 판매범위만 담는다.
    fun changeStoreScope(newScope: StoreScope) {
        this.storeScope = newScope
        registerEvent(ProductStoreScopeChanged(id, newScope))
    }

    fun activate() {
        if (status == ProductStatus.ACTIVE) {
            throw InvalidProductStatusTransitionException(id, status, ProductStatus.ACTIVE)
        }
        status = ProductStatus.ACTIVE
        registerEvent(ProductActivated(id))
    }

    fun discontinue() {
        if (status != ProductStatus.ACTIVE) {
            throw InvalidProductStatusTransitionException(id, status, ProductStatus.DISCONTINUED)
        }
        status = ProductStatus.DISCONTINUED
        registerEvent(ProductDiscontinued(id))
    }

    // Draft가 아닌 상품은 단종 처리(discontinue)를 이용해야 한다.
    fun delete() {
        if (status != ProductStatus.DRAFT) {
            throw ProductNotDeletableException(id, status)
        }
    }

    fun linkOptionGroup(
        optionGroupId: OptionGroupId,
        displayOrder: Int,
    ) {
        if (optionGroupLinks.any { it.id == optionGroupId }) {
            throw DuplicateOptionGroupLinkException(optionGroupId)
        }
        optionGroupLinks = optionGroupLinks + ProductOptionGroupLink(optionGroupId, displayOrder)
    }

    fun unlinkOptionGroup(optionGroupId: OptionGroupId) {
        optionGroupLinks = optionGroupLinks.filterNot { it.id == optionGroupId }
    }

    // order는 연결된 옵션 그룹 전체를 정확히 한 번씩 담은 순열이어야 한다. 일부만 담은
    // 요청을 받아들이면 빠진 링크가 그 링크의 예외 설정(overrides)과 함께 조용히 사라지므로 거부한다.
    fun reorderOptionGroups(order: List<OptionGroupId>) {
        order.forEach { linkOf(it) }
        if (order.size != optionGroupLinks.size || order.distinct().size != order.size) {
            throw InvalidOptionGroupOrderException(id, optionGroupLinks.map { it.id }, order)
        }
        val linksById = optionGroupLinks.associateBy { it.id }
        optionGroupLinks =
            order.mapIndexed { index, optionGroupId ->
                linksById.getValue(optionGroupId).also { it.displayOrder = index }
            }
    }

    // groupOptionKeys는 옵션 그룹의 현재 옵션 키 전체다. application이 옵션 그룹을 불러와 넘기고,
    // Product는 검증에만 쓰고 보관하지 않는다(애그리거트끼리는 ID로만 참조, ADR-0012).
    // 옵션 그룹에 없는 옵션 키에는 예외를 지정할 수 없다.
    fun overrideOptionPrice(
        optionGroupId: OptionGroupId,
        groupOptionKeys: Set<OptionKey>,
        optionKey: OptionKey,
        price: Money,
    ) {
        val link = linkOf(optionGroupId)
        requireOptionKeyExists(optionGroupId, groupOptionKeys, optionKey)
        link.replaceOverrides(overridesReplacing(link, OptionOverride.Price(optionKey, price)))
    }

    // 이 제외를 반영했을 때 선택 가능한 옵션이 0개가 되면 거부한다.
    fun excludeOption(
        optionGroupId: OptionGroupId,
        groupOptionKeys: Set<OptionKey>,
        optionKey: OptionKey,
    ) {
        val link = linkOf(optionGroupId)
        requireOptionKeyExists(optionGroupId, groupOptionKeys, optionKey)
        if ((groupOptionKeys - link.excludedOptionKeys - optionKey).isEmpty()) {
            throw NoSelectableOptionException(id, optionGroupId)
        }
        link.replaceOverrides(overridesReplacing(link, OptionOverride.Exclude(optionKey)))
    }

    fun removeOverride(
        optionGroupId: OptionGroupId,
        optionKey: OptionKey,
    ) {
        val link = linkOf(optionGroupId)
        link.replaceOverrides(link.overrides.filterNot { it.optionKey == optionKey })
    }

    // 옵션 목록 교체로 옵션 그룹에서 사라진 옵션 키의 예외를 삭제한다(요구사항 1.9).
    // 삭제할 키는 OptionReplacementPolicy.check()가 계산한 결과를 받는다. 옵션 그룹의 현재 상태를
    // 보지 않으므로 옵션 그룹 교체와의 호출 순서에 의존하지 않는다. 이후 같은 키가 다시 생겨도 복원하지 않는다.
    fun removeOverrides(
        optionGroupId: OptionGroupId,
        optionKeys: Set<OptionKey>,
    ) {
        val link = linkOf(optionGroupId)
        link.replaceOverrides(link.overrides.filterNot { it.optionKey in optionKeys })
    }

    private fun requireOptionKeyExists(
        optionGroupId: OptionGroupId,
        groupOptionKeys: Set<OptionKey>,
        optionKey: OptionKey,
    ) {
        if (optionKey !in groupOptionKeys) {
            throw OptionKeyNotFoundException(optionGroupId, optionKey)
        }
    }

    private fun overridesReplacing(
        link: ProductOptionGroupLink,
        override: OptionOverride,
    ): List<OptionOverride> = link.overrides.filterNot { it.optionKey == override.optionKey } + override

    private fun linkOf(optionGroupId: OptionGroupId): ProductOptionGroupLink =
        optionGroupLinks.firstOrNull { it.id == optionGroupId }
            ?: throw ProductOptionGroupNotLinkedException(id, optionGroupId)

    class NewProduct private constructor(
        val sku: Sku?,
        val name: String,
        val categoryId: CategoryId,
        val description: String?,
        val imageUrl: String?,
        val basePrice: Money,
        val tracksInventory: Boolean,
        val tagIds: Set<TagId>,
        val groupIds: Set<ProductGroupId>,
        val optionGroupIds: List<OptionGroupId>,
    ) {
        companion object {
            // 검증을 거치지 않고는 인스턴스를 만들 수 없는 유일한 생성 경로
            fun of(
                sku: Sku?,
                name: String,
                categoryId: CategoryId,
                description: String?,
                imageUrl: String?,
                basePrice: Money,
                tracksInventory: Boolean,
                tagIds: Set<TagId> = emptySet(),
                groupIds: Set<ProductGroupId> = emptySet(),
                optionGroupIds: List<OptionGroupId> = emptyList(),
            ): NewProduct {
                validateNoDuplicateOptionGroup(optionGroupIds)
                return NewProduct(
                    sku,
                    name,
                    categoryId,
                    description,
                    imageUrl,
                    basePrice,
                    tracksInventory,
                    tagIds,
                    groupIds,
                    optionGroupIds,
                )
            }
        }
    }

    private companion object {
        fun validateNoDuplicateOptionGroup(optionGroupIds: List<OptionGroupId>) {
            val duplicateId =
                optionGroupIds
                    .groupBy { it }
                    .entries
                    .firstOrNull { it.value.size > 1 }
                    ?.key
            if (duplicateId != null) {
                throw DuplicateOptionGroupLinkException(duplicateId)
            }
        }
    }
}
