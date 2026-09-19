package com.dozycoffee.catalog.domain.product.service

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.domain.optiongroup.exception.EmptyOptionGroupException
import com.dozycoffee.catalog.domain.product.exception.NoSelectableOptionException
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

@DisplayName("OptionReplacementPolicy")
class OptionReplacementPolicyTest {
    @Nested
    @DisplayName("연결 상품 검증")
    inner class Validation {
        @Test
        fun `어떤 연결 상품의 선택 가능한 옵션이 0개가 되면 전체 교체를 거부한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val fine = product(1, ProductStatus.ACTIVE)
            val broken = product(2, ProductStatus.ACTIVE, exclude("VENTI"))

            assertFailsWith<NoSelectableOptionException> {
                OptionReplacementPolicy.check(group, listOf(option("VENTI", 1000)), listOf(fine, broken))
            }
        }

        @Test
        fun `단종 상품도 검사한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val discontinued = product(1, ProductStatus.DISCONTINUED, exclude("VENTI"))

            assertFailsWith<NoSelectableOptionException> {
                OptionReplacementPolicy.check(group, listOf(option("VENTI", 1000)), listOf(discontinued))
            }
        }

        @Test
        fun `Draft 상품도 검사한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val draft = product(1, ProductStatus.DRAFT, exclude("VENTI"))

            assertFailsWith<NoSelectableOptionException> {
                OptionReplacementPolicy.check(group, listOf(option("VENTI", 1000)), listOf(draft))
            }
        }

        @Test
        fun `옵션을 0개로 만들려 하면 상품 검사보다 먼저 옵션 그룹 규칙으로 거부한다`() {
            val group = optionGroup(option("TALL", 0))

            assertFailsWith<EmptyOptionGroupException> {
                OptionReplacementPolicy.check(group, emptyList(), listOf(product(1, ProductStatus.ACTIVE)))
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

            // 호출 코드 오류이므로 DomainException이 아닌 require로 거부한다.
            assertFailsWith<IllegalArgumentException> {
                OptionReplacementPolicy.check(group, listOf(option("GRANDE", 500)), listOf(unlinked))
            }
        }
    }

    @Nested
    @DisplayName("사라지는 옵션 키 계산")
    inner class RemovedKeys {
        @Test
        fun `현재 목록에 있고 새 목록에 없는 옵션 키만 돌려준다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500), option("VENTI", 1000))
            val product = product(1, ProductStatus.ACTIVE)

            val plan =
                OptionReplacementPolicy.check(
                    group,
                    listOf(option("GRANDE", 600), option("TRENTA", 1500)),
                    listOf(product),
                )

            assertEquals(setOf(OptionKey("TALL"), OptionKey("VENTI")), plan.removedOptionKeys)
        }

        @Test
        fun `사라지는 옵션이 없으면 빈 목록을 돌려준다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))

            val plan =
                OptionReplacementPolicy.check(
                    group,
                    listOf(option("GRANDE", 600), option("TALL", 0), option("VENTI", 1000)),
                    listOf(product(1, ProductStatus.ACTIVE)),
                )

            assertEquals(emptySet(), plan.removedOptionKeys)
        }

        @Test
        fun `연결 상품이 없어도 판단할 수 있다`() {
            val group = optionGroup(option("TALL", 0))

            val plan = OptionReplacementPolicy.check(group, listOf(option("GRANDE", 500)), emptyList())

            assertEquals(setOf(OptionKey("TALL")), plan.removedOptionKeys)
        }
    }

    @Nested
    @DisplayName("애그리거트를 바꾸지 않음")
    inner class NoMutation {
        @Test
        fun `통과해도 옵션 목록과 상품 예외를 그대로 둔다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val product = product(1, ProductStatus.ACTIVE, exclude("TALL"), priceOverride("GRANDE", 300))

            OptionReplacementPolicy.check(group, listOf(option("GRANDE", 600), option("VENTI", 1000)), listOf(product))

            assertEquals(listOf(option("TALL", 0), option("GRANDE", 500)), group.options)
            assertEquals(
                listOf(exclude("TALL"), priceOverride("GRANDE", 300)),
                product.optionGroupLinks.single().overrides,
            )
        }

        @Test
        fun `거부되어도 옵션 목록과 상품 예외를 그대로 둔다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val fine = product(1, ProductStatus.ACTIVE, priceOverride("TALL", 100))
            val broken = product(2, ProductStatus.ACTIVE, exclude("VENTI"))

            assertFailsWith<NoSelectableOptionException> {
                OptionReplacementPolicy.check(group, listOf(option("VENTI", 1000)), listOf(fine, broken))
            }

            assertEquals(listOf(option("TALL", 0), option("GRANDE", 500)), group.options)
            assertEquals(listOf(priceOverride("TALL", 100)), fine.optionGroupLinks.single().overrides)
            assertEquals(listOf(exclude("VENTI")), broken.optionGroupLinks.single().overrides)
        }
    }

    @Nested
    @DisplayName("판단 결과대로 교체")
    inner class ApplyingPlan {
        // application이 check() 결과로 수행할 흐름을 애그리거트 메서드로 재현한다.
        private fun replace(
            group: OptionGroup,
            newOptions: List<Option>,
            products: List<Product>,
        ) {
            val plan = OptionReplacementPolicy.check(group, newOptions, products)
            group.replaceOptions(newOptions)
            products.forEach { it.removeOverrides(group.id, plan.removedOptionKeys) }
        }

        @Test
        fun `사라진 옵션 키의 예외는 연결 상품마다 삭제하고 남은 키의 예외는 유지한다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500), option("VENTI", 1000))
            val first = product(1, ProductStatus.ACTIVE, exclude("TALL"), priceOverride("GRANDE", 300))
            val second = product(2, ProductStatus.DISCONTINUED, priceOverride("TALL", 100))

            replace(group, listOf(option("GRANDE", 500), option("VENTI", 1000)), listOf(first, second))

            assertEquals(listOf("GRANDE", "VENTI"), group.options.map { it.optionKey.value })
            assertEquals(listOf(priceOverride("GRANDE", 300)), first.optionGroupLinks.single().overrides)
            assertEquals(emptyList(), second.optionGroupLinks.single().overrides)
        }

        @Test
        fun `사라졌던 옵션 키가 다시 추가되어도 삭제된 예외는 복원되지 않는다`() {
            val group = optionGroup(option("TALL", 0), option("GRANDE", 500))
            val product = product(1, ProductStatus.ACTIVE, exclude("TALL"))

            replace(group, listOf(option("GRANDE", 500)), listOf(product))
            replace(group, listOf(option("TALL", 0), option("GRANDE", 500)), listOf(product))

            assertEquals(emptyList(), product.optionGroupLinks.single().overrides)
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
