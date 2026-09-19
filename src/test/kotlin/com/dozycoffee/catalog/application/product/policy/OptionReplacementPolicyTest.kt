package com.dozycoffee.catalog.application.product.policy

import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.optiongroup.exception.EmptyOptionGroupException
import com.dozycoffee.catalog.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductStatus
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

@DisplayName("OptionReplacementPolicy")
class OptionReplacementPolicyTest {
    @Nested
    @DisplayName("연결 상품 검증")
    inner class Validation {
        @Test
        fun `어떤 연결 상품의 선택 가능한 옵션이 0개가 되면 전체 교체를 거부한다`() {
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500))
            val fine = product(link(1, 0), id = 1, status = ProductStatus.ACTIVE)
            val broken = product(link(1, 0, exclude("VENTI")), id = 2, status = ProductStatus.ACTIVE)

            assertFailsWith<NoSelectableOptionException> {
                OptionReplacementPolicy.check(group, listOf(option("VENTI", 1000)), listOf(fine, broken))
            }
        }

        @Test
        fun `단종 상품도 검사한다`() {
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500))
            val discontinued = product(link(1, 0, exclude("VENTI")), status = ProductStatus.DISCONTINUED)

            assertFailsWith<NoSelectableOptionException> {
                OptionReplacementPolicy.check(group, listOf(option("VENTI", 1000)), listOf(discontinued))
            }
        }

        @Test
        fun `Draft 상품도 검사한다`() {
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500))
            val draft = product(link(1, 0, exclude("VENTI")), status = ProductStatus.DRAFT)

            assertFailsWith<NoSelectableOptionException> {
                OptionReplacementPolicy.check(group, listOf(option("VENTI", 1000)), listOf(draft))
            }
        }

        @Test
        fun `옵션을 0개로 만들려 하면 상품 검사보다 먼저 옵션 그룹 규칙으로 거부한다`() {
            val group = optionGroup(1, option("TALL", 0))

            assertFailsWith<EmptyOptionGroupException> {
                OptionReplacementPolicy.check(group, emptyList(), listOf(product(link(1, 0), status = ProductStatus.ACTIVE)))
            }
        }

        @Test
        fun `이 옵션 그룹을 연결하지 않은 상품이 섞이면 거부한다`() {
            val group = optionGroup(1, option("TALL", 0))
            val unlinked = product(id = 9)

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
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500), option("VENTI", 1000))
            val product = product(link(1, 0), status = ProductStatus.ACTIVE)

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
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500))

            val plan =
                OptionReplacementPolicy.check(
                    group,
                    listOf(option("GRANDE", 600), option("TALL", 0), option("VENTI", 1000)),
                    listOf(product(link(1, 0), status = ProductStatus.ACTIVE)),
                )

            assertEquals(emptySet(), plan.removedOptionKeys)
        }

        @Test
        fun `연결 상품이 없어도 판단할 수 있다`() {
            val group = optionGroup(1, option("TALL", 0))

            val plan = OptionReplacementPolicy.check(group, listOf(option("GRANDE", 500)), emptyList())

            assertEquals(setOf(OptionKey("TALL")), plan.removedOptionKeys)
        }
    }

    @Nested
    @DisplayName("애그리거트를 바꾸지 않음")
    inner class NoMutation {
        @Test
        fun `통과해도 옵션 목록과 상품 예외를 그대로 둔다`() {
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500))
            val product = product(link(1, 0, exclude("TALL"), priceOverride("GRANDE", 300)), status = ProductStatus.ACTIVE)

            OptionReplacementPolicy.check(group, listOf(option("GRANDE", 600), option("VENTI", 1000)), listOf(product))

            assertEquals(listOf(option("TALL", 0), option("GRANDE", 500)), group.options)
            assertEquals(
                listOf(exclude("TALL"), priceOverride("GRANDE", 300)),
                product.optionGroupLinks.single().overrides,
            )
        }

        @Test
        fun `거부되어도 옵션 목록과 상품 예외를 그대로 둔다`() {
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500))
            val fine = product(link(1, 0, priceOverride("TALL", 100)), id = 1, status = ProductStatus.ACTIVE)
            val broken = product(link(1, 0, exclude("VENTI")), id = 2, status = ProductStatus.ACTIVE)

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
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500), option("VENTI", 1000))
            val first = product(link(1, 0, exclude("TALL"), priceOverride("GRANDE", 300)), id = 1, status = ProductStatus.ACTIVE)
            val second = product(link(1, 0, priceOverride("TALL", 100)), id = 2, status = ProductStatus.DISCONTINUED)

            replace(group, listOf(option("GRANDE", 500), option("VENTI", 1000)), listOf(first, second))

            assertEquals(listOf("GRANDE", "VENTI"), group.options.map { it.optionKey.value })
            assertEquals(listOf(priceOverride("GRANDE", 300)), first.optionGroupLinks.single().overrides)
            assertEquals(emptyList(), second.optionGroupLinks.single().overrides)
        }

        @Test
        fun `사라졌던 옵션 키가 다시 추가되어도 삭제된 예외는 복원되지 않는다`() {
            val group = optionGroup(1, option("TALL", 0), option("GRANDE", 500))
            val product = product(link(1, 0, exclude("TALL")), status = ProductStatus.ACTIVE)

            replace(group, listOf(option("GRANDE", 500)), listOf(product))
            replace(group, listOf(option("TALL", 0), option("GRANDE", 500)), listOf(product))

            assertEquals(emptyList(), product.optionGroupLinks.single().overrides)
        }
    }
}
