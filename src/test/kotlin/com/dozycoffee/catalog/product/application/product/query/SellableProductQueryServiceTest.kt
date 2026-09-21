package com.dozycoffee.catalog.product.application.product.query

import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 검증 대상은 조회뿐이라 준비 데이터는 SQL로 직접 넣는다(docs/architecture/testing.md). 상품 ID는 넣은 순서대로 1부터 매겨진다.
@DisplayName("가맹점주의 판매 상품 조회 (요구사항 1.12, 2.1)")
class SellableProductQueryServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: SellableProductQueryService

    private val gangnam = StoreId(10)
    private val hongdae = StoreId(20)
    private val busan = StoreId(30)

    private val americano = ProductId(1)
    private val draft = ProductId(2)
    private val discontinued = ProductId(3)
    private val gangnamOnly = ProductId(4)
    private val hongdaeOnly = ProductId(5)
    private val unassigned = ProductId(6)

    private val beverage = CategoryId(1)
    private val tea = CategoryId(3)
    private val newMenuTag = TagId(1)

    @BeforeEach
    fun prepareCatalog() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1), ('차', 1)")
            execute("INSERT INTO tags (name) VALUES ('신메뉴')")

            insertProduct("아메리카노", status = "ACTIVE", storeScope = "ALL")
            insertProduct("신메뉴 후보", status = "DRAFT", storeScope = "ALL")
            insertProduct("단종된 상품", status = "DISCONTINUED", storeScope = "ALL")
            insertProduct("강남 한정 라떼", status = "ACTIVE", storeScope = "LIMITED")
            insertProduct("홍대 한정 라떼", status = "ACTIVE", storeScope = "LIMITED", categoryId = 3)
            insertProduct("미배정 상품", status = "ACTIVE", storeScope = "LIMITED")
            execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (4, ${gangnam.value}), (5, ${hongdae.value})")
            execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, 1), (4, 1), (2, 1)")
        }

    @Nested
    @DisplayName("판매 범위")
    inner class Scope {
        @Test
        fun `Active이고 매장이 판매 범위에 든 상품만 본사 등록 순으로 돌려준다`() =
            runTest {
                assertEquals(listOf(americano, gangnamOnly), search(setOf(gangnam)).ids())
                assertEquals(listOf(americano, hongdaeOnly), search(setOf(hongdae)).ids())
            }

        @Test
        fun `판매 범위가 전체인 상품만 있는 매장에는 그 상품만 보인다`() =
            runTest {
                assertEquals(listOf(americano), search(setOf(busan)).ids())
            }

        @Test
        fun `매장이 여럿이면 그중 하나라도 판매 범위에 든 상품을 모두 돌려준다`() =
            runTest {
                val page = search(setOf(gangnam, hongdae, busan))

                assertEquals(listOf(americano, gangnamOnly, hongdaeOnly), page.ids())
                assertEquals(3, page.totalElements)
            }

        @Test
        fun `매장이 없으면 판매 범위가 전체인 상품도 보이지 않는다`() =
            runTest {
                val page = search(emptySet())

                assertEquals(emptyList(), page.ids())
                assertEquals(0, page.totalElements)
            }
    }

    @Nested
    @DisplayName("조건")
    inner class Filters {
        @Test
        fun `ids로 거르면 판매할 수 없는 상품과 없는 ID는 오류 없이 빠진다`() =
            runTest {
                val page = search(setOf(gangnam), SellableProductFilter(ids = setOf(americano, draft, hongdaeOnly, ProductId(99))))

                assertEquals(listOf(americano), page.ids())
            }

        @Test
        fun `검색어로 거른다`() =
            runTest {
                assertEquals(
                    listOf(gangnamOnly, hongdaeOnly),
                    search(setOf(gangnam, hongdae), SellableProductFilter(keyword = "한정")).ids(),
                )
            }

        @Test
        fun `대분류로 거르면 그 아래 소분류의 상품이 모두 나오고 소분류로 거르면 그 소분류만 나온다`() =
            runTest {
                assertEquals(
                    listOf(americano, gangnamOnly, hongdaeOnly),
                    search(setOf(gangnam, hongdae), SellableProductFilter(categoryId = beverage)).ids(),
                )
                assertEquals(listOf(hongdaeOnly), search(setOf(gangnam, hongdae), SellableProductFilter(categoryId = tea)).ids())
            }

        @Test
        fun `태그로 거르면 판매할 수 있는 상품 중 그 태그가 붙은 상품만 남는다`() =
            runTest {
                // 태그가 붙은 DRAFT 상품(신메뉴 후보)은 빠진다.
                assertEquals(listOf(americano, gangnamOnly), search(setOf(gangnam), SellableProductFilter(tagId = newMenuTag)).ids())
            }
    }

    @Nested
    @DisplayName("페이징")
    inner class Paging {
        @Test
        fun `판매할 수 있는 상품만 세어 나눈다`() =
            runTest {
                val first = service.search(setOf(gangnam, hongdae), SellableProductFilter(), PageRequest(page = 0, size = 2))
                val second = service.search(setOf(gangnam, hongdae), SellableProductFilter(), PageRequest(page = 1, size = 2))

                assertEquals(listOf(americano, gangnamOnly), first.ids())
                assertEquals(listOf(hongdaeOnly), second.ids())
                assertEquals(3, second.totalElements)
                assertEquals(2, second.totalPages)
            }
    }

    @Nested
    @DisplayName("단건 조회")
    inner class Get {
        @Test
        fun `판매할 수 있는 상품을 돌려준다`() =
            runTest {
                assertEquals("아메리카노", service.get(americano, setOf(gangnam)).name)
                assertEquals("홍대 한정 라떼", service.get(hongdaeOnly, setOf(gangnam, hongdae)).name)
            }

        @Test
        fun `Active가 아니거나 판매 범위 밖이거나 없는 상품은 존재를 드러내지 않고 찾을 수 없다고 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> { service.get(draft, setOf(gangnam)) }
                assertFailsWith<ProductNotFoundException> { service.get(discontinued, setOf(gangnam)) }
                assertFailsWith<ProductNotFoundException> { service.get(hongdaeOnly, setOf(gangnam)) }
                assertFailsWith<ProductNotFoundException> { service.get(unassigned, setOf(gangnam, hongdae)) }
                assertFailsWith<ProductNotFoundException> { service.get(ProductId(99), setOf(gangnam)) }
                assertFailsWith<ProductNotFoundException> { service.get(americano, emptySet()) }
            }
    }

    @Nested
    @DisplayName("유효 옵션 구성 조회")
    inner class EffectiveOptions {
        @BeforeEach
        fun linkSizeOptions() =
            runTest {
                execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('사이즈', 'SINGLE', true)")
                execute(
                    "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                        "VALUES (1, 'TALL', '톨', 0, 0), (1, 'GRANDE', '그란데', 500, 1)",
                )
                execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 1, 0), (5, 1, 0)")
            }

        @Test
        fun `판매할 수 있는 상품의 유효 옵션 구성을 돌려준다`() =
            runTest {
                val config = service.getEffectiveOptions(americano, setOf(gangnam))

                assertEquals(
                    listOf(OptionKey("TALL"), OptionKey("GRANDE")),
                    config.groups
                        .single()
                        .options
                        .map { it.optionKey },
                )
                assertEquals(Money(4500), config.displayStartingPrice)
            }

        @Test
        fun `판매 범위 밖 상품은 찾을 수 없다고 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> { service.getEffectiveOptions(hongdaeOnly, setOf(gangnam)) }
                assertFailsWith<ProductNotFoundException> { service.getEffectiveOptions(draft, setOf(gangnam)) }
            }
    }

    private suspend fun search(
        storeIds: Set<StoreId>,
        filter: SellableProductFilter = SellableProductFilter(),
    ): Page<Product> = service.search(storeIds, filter, PageRequest(page = 0, size = 20))

    private fun Page<Product>.ids(): List<ProductId> = content.map { it.id }

    private suspend fun insertProduct(
        name: String,
        status: String,
        storeScope: String,
        categoryId: Long = 2,
    ) = execute(
        """
        INSERT INTO products (name, category_id, base_price, status, store_scope, tracks_inventory)
        VALUES ('$name', $categoryId, 4500, '$status', '$storeScope', false)
        """.trimIndent(),
    )
}
