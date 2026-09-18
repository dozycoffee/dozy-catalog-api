package com.dozycoffee.catalog.domain.product.service

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.domain.optiongroup.exception.EmptyOptionGroupException
import com.dozycoffee.catalog.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.domain.product.exception.ProductOptionGroupNotLinkedException
import com.dozycoffee.catalog.domain.product.model.OptionOverride
import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.ProductOptionGroupLink
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.shared.Money
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("OptionListReplacer")
class OptionListReplacerTest {
    @Nested
    @DisplayName("연결 상품 검증")
    inner class Validation {
        @Test
        fun `어떤 연결 상품의 선택 가능한 옵션이 0개가 되면 전체 교체를 거부한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val fine = product(1, ProductStatus.ACTIVE)
            val broken = product(2, ProductStatus.ACTIVE, exclude("VENTI"))

            assertFailsWith<NoSelectableOptionException> {
                OptionListReplacer.replace(group, listOf(option("VENTI", 1000)), listOf(fine, broken))
            }
        }

        @Test
        fun `단종 상품도 검사한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val discontinued = product(1, ProductStatus.DISCONTINUED, exclude("VENTI"))

            assertFailsWith<NoSelectableOptionException> {
                OptionListReplacer.replace(group, listOf(option("VENTI", 1000)), listOf(discontinued))
            }
        }

        @Test
        fun `Draft 상품도 검사한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val draft = product(1, ProductStatus.DRAFT, exclude("VENTI"))

            assertFailsWith<NoSelectableOptionException> {
                OptionListReplacer.replace(group, listOf(option("VENTI", 1000)), listOf(draft))
            }
        }

        @Test
        fun `거부되면 옵션 목록과 상품 예외를 그대로 둔다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val fine = product(1, ProductStatus.ACTIVE, priceOverride("TALL", 100))
            val broken = product(2, ProductStatus.ACTIVE, exclude("VENTI"))

            runCatching { OptionListReplacer.replace(group, listOf(option("VENTI", 1000)), listOf(fine, broken)) }

            assertEquals(listOf("TALL", "GRANDE"), group.options.map { it.optionKey.value })
            assertEquals(listOf(priceOverride("TALL", 100)), fine.optionGroupLinks.single().overrides)
        }

        @Test
        fun `옵션을 0개로 만들려 하면 상품 검사보다 먼저 옵션 그룹 규칙으로 거부한다`() {
            val group = optionGroup(option("TALL", 0))

            assertFailsWith<EmptyOptionGroupException> {
                OptionListReplacer.replace(group, emptyList(), listOf(product(1, ProductStatus.ACTIVE)))
            }
        }

        @Test
        fun `이 옵션 그룹을 연결하지 않은 상품이 섞이면 거부한다`() {
            val group = optionGroup(option("TALL", 0))
            val unlinked =
                Product(
                    id = ProductId(9),
                    sku = null,
                    name = "라떼",
                    categoryId = CategoryId(10),
                    description = null,
                    imageUrl = null,
                    basePrice = Money(5000),
                    tracksInventory = false,
                )

            assertFailsWith<ProductOptionGroupNotLinkedException> {
                OptionListReplacer.replace(group, listOf(option("GRANDE", 500)), listOf(unlinked))
            }
        }
    }

    @Nested
    @DisplayName("교체와 예외 정리")
    inner class Replacement {
        @Test
        fun `검증을 통과하면 옵션 목록을 새 목록으로 교체한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val product = product(1, ProductStatus.ACTIVE, exclude("TALL"))

            OptionListReplacer.replace(group, listOf(option("GRANDE", 600), option("VENTI", 1000)), listOf(product))

            assertEquals(listOf("GRANDE", "VENTI"), group.options.map { it.optionKey.value })
        }

        @Test
        fun `새 목록에서 사라진 옵션 키의 예외는 연결 상품마다 삭제하고 남은 키의 예외는 유지한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500), option("VENTI", 1000))
            val first = product(1, ProductStatus.ACTIVE, exclude("TALL"), priceOverride("GRANDE", 300))
            val second = product(2, ProductStatus.DISCONTINUED, priceOverride("TALL", 100))

            OptionListReplacer.replace(group, listOf(option("GRANDE", 500), option("VENTI", 1000)), listOf(first, second))

            assertEquals(listOf(priceOverride("GRANDE", 300)), first.optionGroupLinks.single().overrides)
            assertEquals(emptyList(), second.optionGroupLinks.single().overrides)
        }

        @Test
        fun `사라졌던 옵션 키가 다시 추가되어도 삭제된 예외는 복원되지 않는다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val product = product(1, ProductStatus.ACTIVE, exclude("TALL"))

            OptionListReplacer.replace(group, listOf(option("GRANDE", 500)), listOf(product))
            OptionListReplacer.replace(group, listOf(option("TALL", 0), option("GRANDE", 500)), listOf(product))

            assertEquals(emptyList(), product.optionGroupLinks.single().overrides)
        }

        @Test
        fun `연결 상품이 없어도 교체할 수 있다`() {
            val group = optionGroup(option("TALL", 0))

            OptionListReplacer.replace(group, listOf(option("GRANDE", 500)), emptyList())

            assertEquals(listOf("GRANDE"), group.options.map { it.optionKey.value })
        }
    }

    private fun option(
        key: String,
        price: Long,
    ) = Option(OptionKey(key), name = key, price = Money(price))

    private fun exclude(key: String) = OptionOverride.Exclude(OptionKey(key))

    private fun priceOverride(
        key: String,
        price: Long,
    ) = OptionOverride.Price(OptionKey(key), Money(price))

    private fun optionGroup(vararg options: Option) =
        OptionGroup(
            id = GROUP_ID,
            name = "사이즈",
            selectionType = SelectionType.SINGLE,
            required = true,
            options = options.toList(),
        )

    private fun product(
        id: Long,
        status: ProductStatus,
        vararg overrides: OptionOverride,
    ) = Product(
        id = ProductId(id),
        sku = null,
        name = "아메리카노",
        categoryId = CategoryId(10),
        description = null,
        imageUrl = null,
        basePrice = Money(4500),
        tracksInventory = false,
        optionGroupLinks = listOf(ProductOptionGroupLink(GROUP_ID, 0, overrides.toList())),
        status = status,
    )

    private companion object {
        val GROUP_ID = OptionGroupId(1)
    }
}
