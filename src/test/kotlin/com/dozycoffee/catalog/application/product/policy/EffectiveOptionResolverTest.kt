package com.dozycoffee.catalog.application.product.policy

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.fixture.exclude
import com.dozycoffee.catalog.fixture.link
import com.dozycoffee.catalog.fixture.option
import com.dozycoffee.catalog.fixture.optionGroup
import com.dozycoffee.catalog.fixture.priceOverride
import com.dozycoffee.catalog.fixture.product
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("EffectiveOptionResolver")
class EffectiveOptionResolverTest {
    @Nested
    @DisplayName("유효 옵션")
    inner class EffectiveOptions {
        @Test
        fun `예외가 없으면 옵션 그룹의 옵션 순서와 가격을 그대로 따른다`() {
            val size = optionGroup(1, option("TALL", 0), option("GRANDE", 500), option("VENTI", 1000))
            val product = product(link(1, 0))

            val group = EffectiveOptionResolver.resolve(product, listOf(size)).groups.single()

            assertEquals(listOf("TALL", "GRANDE", "VENTI"), group.options.map { it.optionKey.value })
            assertEquals(listOf(0L, 500L, 1000L), group.options.map { it.price.amount })
            assertTrue(group.options.none { it.priceOverridden })
        }

        @Test
        fun `상품에서 제외한 옵션은 빠진다`() {
            val size = optionGroup(1, option("TALL", 0), option("GRANDE", 500), option("VENTI", 1000))
            val product = product(link(1, 0, exclude("GRANDE")))

            val group = EffectiveOptionResolver.resolve(product, listOf(size)).groups.single()

            assertEquals(listOf("TALL", "VENTI"), group.options.map { it.optionKey.value })
        }

        @Test
        fun `가격 예외가 있으면 그 가격을 적용하고 예외 적용 여부를 표시한다`() {
            val shot = optionGroup(1, option("SHOT", 500), option("SYRUP", 300))
            val product = product(link(1, 0, priceOverride("SHOT", 0)))

            val options =
                EffectiveOptionResolver
                    .resolve(product, listOf(shot))
                    .groups
                    .single()
                    .options

            val shotOption = options.single { it.optionKey == OptionKey("SHOT") }
            assertEquals(Money(0), shotOption.price)
            assertTrue(shotOption.priceOverridden)
            val syrupOption = options.single { it.optionKey == OptionKey("SYRUP") }
            assertEquals(Money(300), syrupOption.price)
            assertFalse(syrupOption.priceOverridden)
        }

        @Test
        fun `그룹은 상품의 연결 순서대로 나열하고 그룹 정보를 담는다`() {
            val size = optionGroup(1, option("TALL", 0), name = "사이즈")
            val shot = optionGroup(2, option("SHOT", 500), name = "샷 추가", required = false, selectionType = SelectionType.MULTI)
            val product =
                product(
                    link(1, 1),
                    link(2, 0),
                )

            val groups = EffectiveOptionResolver.resolve(product, listOf(size, shot)).groups

            assertEquals(listOf(OptionGroupId(2), OptionGroupId(1)), groups.map { it.optionGroupId })
            val shotGroup = groups.first()
            assertEquals("샷 추가", shotGroup.name)
            assertEquals(SelectionType.MULTI, shotGroup.selectionType)
            assertFalse(shotGroup.required)
        }
    }

    @Nested
    @DisplayName("자동 선택")
    inner class AutoSelect {
        @Test
        fun `필수 그룹에 유효 옵션이 1개면 그 옵션이 자동 선택된다`() {
            val size = optionGroup(1, option("TALL", 0), option("GRANDE", 500), required = true)
            val product = product(link(1, 0, exclude("TALL")))

            val group = EffectiveOptionResolver.resolve(product, listOf(size)).groups.single()

            assertEquals(OptionKey("GRANDE"), group.autoSelectedOption?.optionKey)
        }

        @Test
        fun `선택 그룹은 유효 옵션이 1개여도 자동 선택하지 않는다`() {
            val shot = optionGroup(1, option("SHOT", 500), required = false)
            val product = product(link(1, 0))

            val group = EffectiveOptionResolver.resolve(product, listOf(shot)).groups.single()

            assertNull(group.autoSelectedOption)
        }

        @Test
        fun `필수 그룹이라도 유효 옵션이 2개 이상이면 자동 선택하지 않는다`() {
            val size = optionGroup(1, option("TALL", 0), option("GRANDE", 500), required = true)
            val product = product(link(1, 0))

            val group = EffectiveOptionResolver.resolve(product, listOf(size)).groups.single()

            assertNull(group.autoSelectedOption)
        }
    }

    @Nested
    @DisplayName("표시용 시작가")
    inner class DisplayStartingPrice {
        @Test
        fun `기준가에 필수 그룹마다 최저가를 더하고 선택 그룹은 더하지 않는다`() {
            val size = optionGroup(1, option("GRANDE", 500), option("TALL", 300), required = true)
            val bean = optionGroup(2, option("DECAF", 300), required = true)
            val shot = optionGroup(3, option("SHOT", 500), required = false)
            val product =
                product(
                    link(1, 0),
                    link(2, 1),
                    link(3, 2),
                    basePrice = 4500,
                )

            val config = EffectiveOptionResolver.resolve(product, listOf(size, bean, shot))

            // 4500 + 사이즈 최저 300 + 원두 자동 선택 300
            assertEquals(Money(5100), config.displayStartingPrice)
        }

        @Test
        fun `가격 예외가 최저가에 반영된다`() {
            val size = optionGroup(1, option("TALL", 300), option("GRANDE", 500), required = true)
            val product =
                product(
                    link(1, 0, priceOverride("GRANDE", 100)),
                    basePrice = 4500,
                )

            val config = EffectiveOptionResolver.resolve(product, listOf(size))

            assertEquals(Money(4600), config.displayStartingPrice)
        }

        @Test
        fun `제외된 옵션은 최저가 계산에서 빠진다`() {
            val size = optionGroup(1, option("TALL", 0), option("GRANDE", 500), required = true)
            val product =
                product(
                    link(1, 0, exclude("TALL")),
                    basePrice = 4500,
                )

            val config = EffectiveOptionResolver.resolve(product, listOf(size))

            assertEquals(Money(5000), config.displayStartingPrice)
        }

        @Test
        fun `연결된 옵션 그룹이 없으면 기준가가 곧 시작가다`() {
            val config = EffectiveOptionResolver.resolve(product(basePrice = 4500), emptyList())

            assertEquals(Money(4500), config.displayStartingPrice)
        }
    }

    @Nested
    @DisplayName("입력 옵션 그룹 검증")
    inner class InputValidation {
        @Test
        fun `상품에 연결되지 않은 옵션 그룹을 넘기면 거부한다`() {
            val size = optionGroup(1, option("TALL", 0))
            val unlinked = optionGroup(99, option("SHOT", 500))
            val product = product(link(1, 0))

            // 호출 코드 오류이므로 DomainException이 아닌 require로 거부한다.
            assertFailsWith<IllegalArgumentException> {
                EffectiveOptionResolver.resolve(product, listOf(size, unlinked))
            }
        }

        @Test
        fun `연결된 옵션 그룹이 빠져 있으면 거부한다`() {
            val size = optionGroup(1, option("TALL", 0))
            val product =
                product(
                    link(1, 0),
                    link(2, 1),
                )

            assertFailsWith<IllegalArgumentException> {
                EffectiveOptionResolver.resolve(product, listOf(size))
            }
        }
    }
}
