package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
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
import com.dozycoffee.catalog.fixture.exclude
import com.dozycoffee.catalog.fixture.link
import com.dozycoffee.catalog.fixture.newProduct
import com.dozycoffee.catalog.fixture.priceOverride
import com.dozycoffee.catalog.fixture.product
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("Product")
class ProductTest {
    @Nested
    @DisplayName("활성화")
    inner class Activate {
        @Test
        fun `Draft 상품을 활성화한다`() {
            val product = product(status = ProductStatus.DRAFT)

            product.activate()

            assertEquals(ProductStatus.ACTIVE, product.status)
            assertIs<ProductActivated>(product.pullDomainEvents().single())
        }

        @Test
        fun `단종된 상품은 다시 활성화할 수 있다`() {
            val product = product(status = ProductStatus.DISCONTINUED)

            product.activate()

            assertEquals(ProductStatus.ACTIVE, product.status)
        }

        @Test
        fun `이미 Active인 상품의 활성화 요청은 거부된다`() {
            val product = product(status = ProductStatus.ACTIVE)

            assertFailsWith<InvalidProductStatusTransitionException> { product.activate() }
        }

        @Test
        fun `거부된 활성화는 이벤트를 등록하지 않는다`() {
            val product = product(status = ProductStatus.ACTIVE)

            runCatching { product.activate() }

            assertTrue(product.pullDomainEvents().isEmpty())
        }
    }

    @Nested
    @DisplayName("단종")
    inner class Discontinue {
        @Test
        fun `Active 상품을 단종한다`() {
            val product = product(status = ProductStatus.ACTIVE)

            product.discontinue()

            assertEquals(ProductStatus.DISCONTINUED, product.status)
            assertIs<ProductDiscontinued>(product.pullDomainEvents().single())
        }

        @Test
        fun `Draft 상품은 단종으로 직접 전환할 수 없다`() {
            // 등록 취소는 삭제(1.11)를 이용해야 한다.
            val product = product(status = ProductStatus.DRAFT)

            assertFailsWith<InvalidProductStatusTransitionException> { product.discontinue() }
        }

        @Test
        fun `이미 단종된 상품의 단종 요청은 거부된다`() {
            val product = product(status = ProductStatus.DISCONTINUED)

            assertFailsWith<InvalidProductStatusTransitionException> { product.discontinue() }
        }
    }

    @Nested
    @DisplayName("삭제 가드")
    inner class Delete {
        @Test
        fun `Draft 상품은 삭제할 수 있다`() {
            val product = product(status = ProductStatus.DRAFT)

            product.delete()
        }

        @Test
        fun `Active 상품은 삭제할 수 없다`() {
            val product = product(status = ProductStatus.ACTIVE)

            assertFailsWith<ProductNotDeletableException> { product.delete() }
        }

        @Test
        fun `단종된 상품은 삭제할 수 없다`() {
            val product = product(status = ProductStatus.DISCONTINUED)

            assertFailsWith<ProductNotDeletableException> { product.delete() }
        }
    }

    @Nested
    @DisplayName("옵션 그룹 연결")
    inner class OptionGroupLink {
        @Test
        fun `옵션 그룹을 연결한다`() {
            val product = product()

            product.linkOptionGroup(OptionGroupId(1), displayOrder = 0)

            assertEquals(listOf(OptionGroupId(1)), product.optionGroupLinks.map { it.id })
        }

        @Test
        fun `같은 옵션 그룹을 두 번 연결할 수 없다`() {
            val product = product()
            product.linkOptionGroup(OptionGroupId(1), displayOrder = 0)

            assertFailsWith<DuplicateOptionGroupLinkException> {
                product.linkOptionGroup(OptionGroupId(1), displayOrder = 1)
            }
        }

        @Test
        fun `연결을 해제한다`() {
            val product = product(link(1, 0), link(2, 1))

            product.unlinkOptionGroup(OptionGroupId(1))

            assertEquals(listOf(OptionGroupId(2)), product.optionGroupLinks.map { it.id })
        }

        @Test
        fun `연결된 그룹 전체를 다시 정렬하면 표시 순서가 바뀐다`() {
            val product = product(link(1, 0), link(2, 1))

            product.reorderOptionGroups(listOf(OptionGroupId(2), OptionGroupId(1)))

            assertEquals(listOf(OptionGroupId(2), OptionGroupId(1)), product.optionGroupLinks.map { it.id })
            assertEquals(listOf(0, 1), product.optionGroupLinks.map { it.displayOrder })
        }

        @Test
        fun `다시 정렬해도 각 연결의 예외 설정은 유지된다`() {
            val product = product(link(1, 0, exclude("TALL")), link(2, 1))

            product.reorderOptionGroups(listOf(OptionGroupId(2), OptionGroupId(1)))

            assertEquals(listOf(exclude("TALL")), product.optionGroupLinks.single { it.id == OptionGroupId(1) }.overrides)
        }

        @Test
        fun `연결된 그룹 일부만 담아 정렬하면 거부하고 기존 연결을 그대로 둔다`() {
            val product = product(link(1, 0), link(2, 1))

            assertFailsWith<InvalidOptionGroupOrderException> {
                product.reorderOptionGroups(listOf(OptionGroupId(1)))
            }
            assertEquals(listOf(OptionGroupId(1), OptionGroupId(2)), product.optionGroupLinks.map { it.id })
            assertEquals(listOf(0, 1), product.optionGroupLinks.map { it.displayOrder })
        }

        @Test
        fun `같은 그룹을 중복해 담으면 거부한다`() {
            val product = product(link(1, 0), link(2, 1))

            assertFailsWith<InvalidOptionGroupOrderException> {
                product.reorderOptionGroups(listOf(OptionGroupId(1), OptionGroupId(1)))
            }
            assertEquals(listOf(OptionGroupId(1), OptionGroupId(2)), product.optionGroupLinks.map { it.id })
        }

        @Test
        fun `연결되지 않은 그룹을 담으면 연결되지 않은 옵션 그룹 예외로 거부한다`() {
            val product = product(link(1, 0))

            assertFailsWith<ProductOptionGroupNotLinkedException> {
                product.reorderOptionGroups(listOf(OptionGroupId(99)))
            }
            assertEquals(listOf(OptionGroupId(1)), product.optionGroupLinks.map { it.id })
        }
    }

    @Nested
    @DisplayName("상품별 옵션 예외")
    inner class OptionOverrides {
        private val sizeGroupId = OptionGroupId(1)
        private val sizeGroupKeys = setOf(OptionKey("TALL"), OptionKey("GRANDE"))

        @Test
        fun `특정 옵션의 가격을 이 상품에서만 다르게 지정한다`() {
            val product = product(link(1, 0))

            product.overrideOptionPrice(sizeGroupId, sizeGroupKeys, OptionKey("GRANDE"), Money(700))

            val override =
                assertIs<OptionOverride.Price>(
                    product.optionGroupLinks
                        .single()
                        .overrides
                        .single(),
                )
            assertEquals(OptionKey("GRANDE"), override.optionKey)
            assertEquals(Money(700), override.price)
        }

        @Test
        fun `같은 옵션에 예외를 다시 지정하면 누적되지 않고 교체된다`() {
            val product = product(link(1, 0))
            product.overrideOptionPrice(sizeGroupId, sizeGroupKeys, OptionKey("GRANDE"), Money(700))

            product.overrideOptionPrice(sizeGroupId, sizeGroupKeys, OptionKey("GRANDE"), Money(900))

            val override =
                assertIs<OptionOverride.Price>(
                    product.optionGroupLinks
                        .single()
                        .overrides
                        .single(),
                )
            assertEquals(Money(900), override.price)
        }

        @Test
        fun `옵션을 이 상품에서만 제외한다`() {
            val product = product(link(1, 0))

            product.excludeOption(sizeGroupId, sizeGroupKeys, OptionKey("TALL"))

            val override =
                assertIs<OptionOverride.Exclude>(
                    product.optionGroupLinks
                        .single()
                        .overrides
                        .single(),
                )
            assertEquals(OptionKey("TALL"), override.optionKey)
        }

        @Test
        fun `가격 예외가 있던 옵션을 제외하면 제외로 교체된다`() {
            val product = product(link(1, 0))
            product.overrideOptionPrice(sizeGroupId, sizeGroupKeys, OptionKey("TALL"), Money(300))

            product.excludeOption(sizeGroupId, sizeGroupKeys, OptionKey("TALL"))

            assertIs<OptionOverride.Exclude>(
                product.optionGroupLinks
                    .single()
                    .overrides
                    .single(),
            )
        }

        @Test
        fun `제외로 선택 가능한 옵션이 0개가 되면 거부하고 기존 예외를 그대로 둔다`() {
            val product = product(link(1, 0))
            product.excludeOption(sizeGroupId, sizeGroupKeys, OptionKey("TALL"))

            assertFailsWith<NoSelectableOptionException> {
                product.excludeOption(sizeGroupId, sizeGroupKeys, OptionKey("GRANDE"))
            }
            assertEquals(
                listOf(OptionKey("TALL")),
                product.optionGroupLinks
                    .single()
                    .overrides
                    .map { it.optionKey },
            )
        }

        @Test
        fun `옵션이 1개뿐인 그룹에서 그 옵션을 제외하면 거부된다`() {
            val product = product(link(1, 0))

            assertFailsWith<NoSelectableOptionException> {
                product.excludeOption(OptionGroupId(1), setOf(OptionKey("ONLY")), OptionKey("ONLY"))
            }
        }

        @Test
        fun `옵션 그룹에 없는 옵션 키에는 가격 예외를 지정할 수 없다`() {
            val product = product(link(1, 0))

            val exception =
                assertFailsWith<OptionKeyNotFoundException> {
                    product.overrideOptionPrice(sizeGroupId, sizeGroupKeys, OptionKey("VENTI"), Money(700))
                }
            assertEquals(ErrorType.NOT_FOUND, exception.errorCode.type)
            assertTrue(
                product.optionGroupLinks
                    .single()
                    .overrides
                    .isEmpty(),
            )
        }

        @Test
        fun `옵션 그룹에 없는 옵션 키는 제외할 수 없다`() {
            val product = product(link(1, 0))

            assertFailsWith<OptionKeyNotFoundException> {
                product.excludeOption(sizeGroupId, sizeGroupKeys, OptionKey("VENTI"))
            }
        }

        @Test
        fun `연결되지 않은 옵션 그룹에는 예외를 지정할 수 없다`() {
            val product = product(link(1, 0))

            assertFailsWith<ProductOptionGroupNotLinkedException> {
                product.overrideOptionPrice(OptionGroupId(99), setOf(OptionKey("SHOT")), OptionKey("SHOT"), Money(700))
            }
        }

        @Test
        fun `지정한 예외를 제거한다`() {
            val product = product(link(1, 0))
            product.overrideOptionPrice(sizeGroupId, sizeGroupKeys, OptionKey("GRANDE"), Money(700))

            product.removeOverride(OptionGroupId(1), OptionKey("GRANDE"))

            assertTrue(
                product.optionGroupLinks
                    .single()
                    .overrides
                    .isEmpty(),
            )
        }

        @Test
        fun `주어진 옵션 키의 예외만 삭제하고 나머지는 유지한다`() {
            val product =
                product(
                    link(1, 0, exclude("TALL"), priceOverride("GRANDE", 700), priceOverride("VENTI", 900)),
                )

            product.removeOverrides(OptionGroupId(1), setOf(OptionKey("TALL"), OptionKey("VENTI")))

            assertEquals(
                listOf(OptionKey("GRANDE")),
                product.optionGroupLinks
                    .single()
                    .overrides
                    .map { it.optionKey },
            )
        }

        @Test
        fun `다른 옵션 그룹의 예외는 건드리지 않는다`() {
            val product = product(link(1, 0, exclude("TALL")), link(2, 1, exclude("TALL")))

            product.removeOverrides(OptionGroupId(1), setOf(OptionKey("TALL")))

            assertEquals(emptyList(), product.optionGroupLinks.first { it.id == OptionGroupId(1) }.overrides)
            assertEquals(
                listOf(exclude("TALL")),
                product.optionGroupLinks.first { it.id == OptionGroupId(2) }.overrides,
            )
        }

        @Test
        fun `연결되지 않은 옵션 그룹의 예외는 삭제할 수 없다`() {
            val product = product(link(1, 0))

            assertFailsWith<ProductOptionGroupNotLinkedException> {
                product.removeOverrides(OptionGroupId(99), setOf(OptionKey("TALL")))
            }
        }
    }

    @Nested
    @DisplayName("신규 등록")
    inner class Registration {
        @Test
        fun `같은 옵션 그룹을 중복 지정하면 등록할 수 없다`() {
            assertFailsWith<DuplicateOptionGroupLinkException> {
                newProduct(optionGroupIds = listOf(OptionGroupId(1), OptionGroupId(1)))
            }
        }

        @Test
        fun `검증을 통과하면 입력값을 그대로 보관한다`() {
            val newProduct = newProduct(optionGroupIds = listOf(OptionGroupId(1), OptionGroupId(2)), name = "아메리카노")

            assertEquals("아메리카노", newProduct.name)
            assertEquals(listOf(OptionGroupId(1), OptionGroupId(2)), newProduct.optionGroupIds)
        }

        @Test
        fun `지정한 카테고리의 ID를 참조한다`() {
            val newProduct = newProduct(optionGroupIds = emptyList(), categoryId = 20)

            assertEquals(CategoryId(20), newProduct.categoryId)
        }
    }

    @Nested
    @DisplayName("판매 범위")
    inner class StoreScopeChange {
        @Test
        fun `판매 범위를 변경하면 새 범위를 담은 이벤트를 등록한다`() {
            val product = product(status = ProductStatus.ACTIVE)
            val newScope = StoreScope.Limited(setOf(StoreId(1), StoreId(2)))

            product.changeStoreScope(newScope)

            val event = assertIs<ProductStoreScopeChanged>(product.pullDomainEvents().single())
            assertEquals(newScope, event.newScope)
        }

        @Test
        fun `전체 판매 범위는 모든 매장을 포함한다`() {
            assertTrue(StoreScope.All.covers(StoreId(1)))
        }

        @Test
        fun `한정 판매 범위는 지정한 매장만 포함한다`() {
            val scope = StoreScope.Limited(setOf(StoreId(1)))

            assertTrue(scope.covers(StoreId(1)))
            assertEquals(false, scope.covers(StoreId(2)))
        }

        @Test
        fun `대상 매장이 비어 있으면 어떤 매장도 포함하지 않는다`() {
            // 대상 매장 목록은 비워둘 수 있다(1.5).
            val scope = StoreScope.Limited(emptySet())

            assertEquals(false, scope.covers(StoreId(1)))
        }
    }

    @Nested
    @DisplayName("정보 수정")
    inner class Modification {
        @Test
        fun `단종 상태에서도 다른 모든 정보를 수정할 수 있다`() {
            val product = product(status = ProductStatus.DISCONTINUED)

            product.rename("디카페인 아메리카노")
            product.changeBasePrice(Money(5000))
            product.changeCategory(CategoryId(99))

            assertEquals("디카페인 아메리카노", product.name)
            assertEquals(Money(5000), product.basePrice)
            assertEquals(CategoryId(99), product.categoryId)
            assertEquals(ProductStatus.DISCONTINUED, product.status)
        }

        @Test
        fun `카테고리를 바꾸면 새 카테고리의 ID를 참조한다`() {
            val product = product()

            product.changeCategory(CategoryId(30))

            assertEquals(CategoryId(30), product.categoryId)
        }
    }
}
