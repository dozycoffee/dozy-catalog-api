package com.dozycoffee.catalog.fixture

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.product.model.OptionOverride
import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.ProductOptionGroupLink
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.shared.Money

// 기본값은 등록 직후 상태(DRAFT)다. 다른 상태가 필요하면 테스트에서 명시한다.
fun product(
    vararg links: ProductOptionGroupLink,
    id: Long = 1,
    status: ProductStatus = ProductStatus.DRAFT,
    basePrice: Long = 4500,
    tracksInventory: Boolean = false,
    storeScope: StoreScope = StoreScope.All,
    categoryId: Long = 10,
    version: Long = 0,
) = Product(
    id = ProductId(id),
    sku = null,
    name = "아메리카노",
    categoryId = CategoryId(categoryId),
    description = null,
    imageUrl = null,
    basePrice = Money(basePrice),
    tracksInventory = tracksInventory,
    optionGroupLinks = links.toList(),
    status = status,
    storeScope = storeScope,
    version = version,
)

fun link(
    optionGroupId: Long,
    displayOrder: Int = 0,
    vararg overrides: OptionOverride,
) = ProductOptionGroupLink(OptionGroupId(optionGroupId), displayOrder, overrides.toList())

fun exclude(key: String) = OptionOverride.Exclude(OptionKey(key))

fun priceOverride(
    key: String,
    price: Long,
) = OptionOverride.Price(OptionKey(key), Money(price))

fun newProduct(
    optionGroupIds: List<OptionGroupId> = emptyList(),
    categoryId: Long = 10,
    name: String = "아메리카노",
) = Product.NewProduct.of(
    sku = null,
    name = name,
    categoryId = CategoryId(categoryId),
    description = null,
    imageUrl = null,
    basePrice = Money(4500),
    tracksInventory = false,
    optionGroupIds = optionGroupIds,
)
