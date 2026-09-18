package com.dozycoffee.catalog.domain.product.model

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.category.ChildCategory
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.product.event.ProductActivated
import com.dozycoffee.catalog.domain.product.event.ProductDiscontinued
import com.dozycoffee.catalog.domain.product.event.ProductStoreScopeChanged
import com.dozycoffee.catalog.domain.product.exception.DuplicateOptionGroupLinkException
import com.dozycoffee.catalog.domain.product.exception.InvalidOptionGroupOrderException
import com.dozycoffee.catalog.domain.product.exception.InvalidProductStatusTransitionException
import com.dozycoffee.catalog.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.domain.product.exception.ProductNotDeletableException
import com.dozycoffee.catalog.domain.product.exception.ProductOptionGroupNotLinkedException
import com.dozycoffee.catalog.domain.shared.Money
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
            val product = product(links = listOf(link(1, 0), link(2, 1)))

            product.unlinkOptionGroup(OptionGroupId(1))

            assertEquals(listOf(OptionGroupId(2)), product.optionGroupLinks.map { it.id })
        }

        @Test
        fun `연결된 그룹 전체를 다시 정렬하면 표시 순서가 바뀐다`() {
            val product = product(links = listOf(link(1, 0), link(2, 1)))

            product.reorderOptionGroups(listOf(OptionGroupId(2), OptionGroupId(1)))

            assertEquals(listOf(OptionGroupId(2), OptionGroupId(1)), product.optionGroupLinks.map { it.id })
            assertEquals(listOf(0, 1), product.optionGroupLinks.map { it.displayOrder })
        }

        @Test
        fun `다시 정렬해도 각 연결의 예외 설정은 유지된다`() {
            val exclude = OptionOverride.Exclude(OptionKey("TALL"))
            val product =
                product(
                    links =
                        listOf(
                            ProductOptionGroupLink(OptionGroupId(1), 0, listOf(exclude)),
                            link(2, 1),
                        ),
                )

            product.reorderOptionGroups(listOf(OptionGroupId(2), OptionGroupId(1)))

            assertEquals(listOf(exclude), product.optionGroupLinks.single { it.id == OptionGroupId(1) }.overrides)
        }

        @Test
        fun `연결된 그룹 일부만 담아 정렬하면 거부하고 기존 연결을 그대로 둔다`() {
            val product = product(links = listOf(link(1, 0), link(2, 1)))

            assertFailsWith<InvalidOptionGroupOrderException> {
                product.reorderOptionGroups(listOf(OptionGroupId(1)))
            }
            assertEquals(listOf(OptionGroupId(1), OptionGroupId(2)), product.optionGroupLinks.map { it.id })
            assertEquals(listOf(0, 1), product.optionGroupLinks.map { it.displayOrder })
        }

        @Test
        fun `같은 그룹을 중복해 담으면 거부한다`() {
            val product = product(links = listOf(link(1, 0), link(2, 1)))

            assertFailsWith<InvalidOptionGroupOrderException> {
                product.reorderOptionGroups(listOf(OptionGroupId(1), OptionGroupId(1)))
            }
            assertEquals(listOf(OptionGroupId(1), OptionGroupId(2)), product.optionGroupLinks.map { it.id })
        }

        @Test
        fun `연결되지 않은 그룹을 담으면 연결되지 않은 옵션 그룹 예외로 거부한다`() {
            val product = product(links = listOf(link(1, 0)))

            assertFailsWith<ProductOptionGroupNotLinkedException> {
                product.reorderOptionGroups(listOf(OptionGroupId(99)))
            }
            assertEquals(listOf(OptionGroupId(1)), product.optionGroupLinks.map { it.id })
        }
    }

    @Nested
    @DisplayName("상품별 옵션 예외")
    inner class OptionOverrides {
        @Test
        fun `특정 옵션의 가격을 이 상품에서만 다르게 지정한다`() {
            val product = product(links = listOf(link(1, 0)))

            product.overrideOptionPrice(OptionGroupId(1), OptionKey("SHOT"), Money(700))

            val override =
                assertIs<OptionOverride.Price>(
                    product.optionGroupLinks
                        .single()
                        .overrides
                        .single(),
                )
            assertEquals(OptionKey("SHOT"), override.optionKey)
            assertEquals(Money(700), override.price)
        }

        @Test
        fun `같은 옵션에 예외를 다시 지정하면 누적되지 않고 교체된다`() {
            val product = product(links = listOf(link(1, 0)))
            product.overrideOptionPrice(OptionGroupId(1), OptionKey("SHOT"), Money(700))

            product.overrideOptionPrice(OptionGroupId(1), OptionKey("SHOT"), Money(900))

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
            val product = product(links = listOf(link(1, 0)))

            product.excludeOption(
                OptionGroupId(1),
                OptionKey("TALL"),
                remainingSelectableOptionKeys = setOf(OptionKey("GRANDE")),
            )

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
        fun `제외로 선택 가능한 옵션이 0개가 되면 거부된다`() {
            val product = product(links = listOf(link(1, 0)))

            assertFailsWith<NoSelectableOptionException> {
                product.excludeOption(
                    OptionGroupId(1),
                    OptionKey("TALL"),
                    remainingSelectableOptionKeys = emptySet(),
                )
            }
        }

        @Test
        fun `연결되지 않은 옵션 그룹에는 예외를 지정할 수 없다`() {
            val product = product(links = listOf(link(1, 0)))

            assertFailsWith<ProductOptionGroupNotLinkedException> {
                product.overrideOptionPrice(OptionGroupId(99), OptionKey("SHOT"), Money(700))
            }
        }

        @Test
        fun `지정한 예외를 제거한다`() {
            val product = product(links = listOf(link(1, 0)))
            product.overrideOptionPrice(OptionGroupId(1), OptionKey("SHOT"), Money(700))

            product.removeOverride(OptionGroupId(1), OptionKey("SHOT"))

            assertTrue(
                product.optionGroupLinks
                    .single()
                    .overrides
                    .isEmpty(),
            )
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
            val newProduct = newProduct(optionGroupIds = listOf(OptionGroupId(1), OptionGroupId(2)))

            assertEquals("아메리카노", newProduct.name)
            assertEquals(listOf(OptionGroupId(1), OptionGroupId(2)), newProduct.optionGroupIds)
        }

        @Test
        fun `소분류를 카테고리로 지정해 등록하면 그 소분류의 ID를 참조한다`() {
            val newProduct = newProduct(optionGroupIds = emptyList(), category = childCategory(id = 20))

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
            product.changeCategory(childCategory(id = 99))

            assertEquals("디카페인 아메리카노", product.name)
            assertEquals(Money(5000), product.basePrice)
            assertEquals(CategoryId(99), product.categoryId)
            assertEquals(ProductStatus.DISCONTINUED, product.status)
        }

        @Test
        fun `카테고리를 다른 소분류로 바꾸면 그 소분류의 ID를 참조한다`() {
            val product = product()

            product.changeCategory(childCategory(id = 30))

            assertEquals(CategoryId(30), product.categoryId)
        }
    }

    private fun product(
        status: ProductStatus = ProductStatus.DRAFT,
        storeScope: StoreScope = StoreScope.All,
        links: List<ProductOptionGroupLink> = emptyList(),
    ) = Product(
        id = ProductId(1),
        sku = null,
        name = "아메리카노",
        categoryId = CategoryId(10),
        description = null,
        imageUrl = null,
        basePrice = Money(4500),
        tracksInventory = false,
        optionGroupLinks = links,
        status = status,
        storeScope = storeScope,
    )

    private fun link(
        optionGroupId: Long,
        displayOrder: Int,
    ) = ProductOptionGroupLink(OptionGroupId(optionGroupId), displayOrder)

    private fun childCategory(id: Long) = ChildCategory(CategoryId(id), "커피", parentId = CategoryId(1))

    private fun newProduct(
        optionGroupIds: List<OptionGroupId>,
        category: ChildCategory = childCategory(id = 10),
    ) = Product.NewProduct.of(
        sku = null,
        name = "아메리카노",
        category = category,
        description = null,
        imageUrl = null,
        basePrice = Money(4500),
        tracksInventory = false,
        optionGroupIds = optionGroupIds,
    )
}
