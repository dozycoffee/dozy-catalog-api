package com.dozycoffee.catalog.product.application.product.query

import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
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

// 검증 대상은 조회뿐이라 카테고리·태그·그룹·상품 같은 준비 데이터는 다른 유스케이스를 거치지 않고
// SQL로 직접 넣는다(docs/architecture/testing.md). 상품 ID는 넣은 순서대로 1부터 매겨진다.
@DisplayName("본사관리자의 상품 목록 검색 (요구사항 1.12)")
class ProductQueryServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ProductQueryService

    private val americano = ProductId(1)
    private val latte = ProductId(2)
    private val earlGrey = ProductId(3)
    private val tumbler = ProductId(4)
    private val icedAmericano = ProductId(5)
    private val coldBrew = ProductId(6)

    private val beverage = CategoryId(1)
    private val coffee = CategoryId(2)
    private val tea = CategoryId(3)
    private val md = CategoryId(4)

    private val newMenuTag = TagId(1)
    private val bestTag = TagId(2)
    private val seasonGroup = ProductGroupId(1)

    @BeforeEach
    fun prepareCatalog() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1), ('차', 1)")
            execute("INSERT INTO categories (name) VALUES ('MD')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('텀블러', 4)")
            execute("INSERT INTO tags (name) VALUES ('신메뉴'), ('베스트')")
            execute("INSERT INTO product_groups (name) VALUES ('시즌 운영')")

            insertProduct("아메리카노", sku = "DZ-00000001", categoryId = 2, status = "ACTIVE")
            insertProduct("카페라떼", sku = "DZ-00000002", categoryId = 2, status = "DRAFT")
            insertProduct("얼그레이", sku = "DZ-00000003", categoryId = 3, status = "ACTIVE")
            insertProduct("텀블러", sku = "DZ-00000004", categoryId = 5, status = "DISCONTINUED")
            insertProduct("아이스 아메리카노", sku = "DZ-00000005", categoryId = 2, status = "ACTIVE")
            insertProduct("Cold Brew", sku = "DZ-00000006", categoryId = 2, status = "ACTIVE")
            execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, 1), (5, 1), (3, 2)")
            execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (5, 1), (2, 1)")
        }

    @Nested
    @DisplayName("조건 없이 조회")
    inner class NoFilter {
        @Test
        fun `상태와 관계없이 모든 상품을 최근 등록 순으로 돌려준다`() =
            runTest {
                val page = service.search(ProductSearchFilter(), PageRequest(page = 0, size = 20))

                assertEquals(listOf(coldBrew, icedAmericano, tumbler, earlGrey, latte, americano), page.ids())
                assertEquals(6, page.totalElements)
            }

        @Test
        fun `상품의 하위 정보까지 복원해 돌려준다`() =
            runTest {
                val product = service.search(ProductSearchFilter(ids = setOf(icedAmericano))).content.single()

                assertEquals("아이스 아메리카노", product.name)
                assertEquals(setOf(newMenuTag), product.tagIds)
                assertEquals(setOf(seasonGroup), product.groupIds)
            }
    }

    @Nested
    @DisplayName("검색어")
    inner class Keyword {
        @Test
        fun `상품명의 일부로 찾는다`() =
            runTest {
                assertEquals(listOf(icedAmericano, americano), search(ProductSearchFilter(keyword = "아메리카노")).ids())
            }

        @Test
        fun `상품명은 대소문자를 가리지 않는다`() =
            runTest {
                assertEquals(listOf(coldBrew), search(ProductSearchFilter(keyword = "cold")).ids())
            }

        @Test
        fun `SKU는 완전히 일치해야 찾는다`() =
            runTest {
                assertEquals(listOf(earlGrey), search(ProductSearchFilter(keyword = "DZ-00000003")).ids())
                assertEquals(emptyList(), search(ProductSearchFilter(keyword = "DZ-0000000")).ids())
            }

        @Test
        fun `검색어의 퍼센트와 밑줄은 와일드카드가 아니라 글자로 찾는다`() =
            runTest {
                assertEquals(emptyList(), search(ProductSearchFilter(keyword = "%")).ids())
                assertEquals(emptyList(), search(ProductSearchFilter(keyword = "_")).ids())
            }

        @Test
        fun `공백뿐인 검색어는 거르지 않는다`() =
            runTest {
                assertEquals(6, search(ProductSearchFilter(keyword = "  ")).totalElements)
            }
    }

    @Nested
    @DisplayName("카테고리·태그·그룹·상태")
    inner class Filters {
        @Test
        fun `소분류로 거른다`() =
            runTest {
                assertEquals(listOf(earlGrey), search(ProductSearchFilter(categoryId = tea)).ids())
            }

        @Test
        fun `대분류로 거르면 그 아래 소분류의 상품이 모두 나온다`() =
            runTest {
                assertEquals(
                    listOf(coldBrew, icedAmericano, earlGrey, latte, americano),
                    search(ProductSearchFilter(categoryId = beverage)).ids(),
                )
                assertEquals(listOf(tumbler), search(ProductSearchFilter(categoryId = md)).ids())
            }

        @Test
        fun `태그로 거른다`() =
            runTest {
                assertEquals(listOf(icedAmericano, americano), search(ProductSearchFilter(tagId = newMenuTag)).ids())
            }

        @Test
        fun `상품 그룹으로 거른다`() =
            runTest {
                assertEquals(listOf(icedAmericano, latte), search(ProductSearchFilter(groupId = seasonGroup)).ids())
            }

        @Test
        fun `상태로 거른다`() =
            runTest {
                assertEquals(listOf(latte), search(ProductSearchFilter(status = ProductStatus.DRAFT)).ids())
                assertEquals(listOf(tumbler), search(ProductSearchFilter(status = ProductStatus.DISCONTINUED)).ids())
            }

        @Test
        fun `여러 조건을 함께 주면 모두 만족하는 상품만 남는다`() =
            runTest {
                assertEquals(
                    listOf(icedAmericano),
                    search(ProductSearchFilter(keyword = "아메리카노", tagId = newMenuTag, groupId = seasonGroup)).ids(),
                )
                assertEquals(
                    listOf(americano),
                    search(ProductSearchFilter(categoryId = coffee, status = ProductStatus.ACTIVE, keyword = "DZ-00000001")).ids(),
                )
                assertEquals(emptyList(), search(ProductSearchFilter(tagId = bestTag, categoryId = coffee)).ids())
            }
    }

    @Nested
    @DisplayName("ids")
    inner class Ids {
        @Test
        fun `주어진 ID의 상품만 돌려주고 없는 ID는 오류 없이 빠진다`() =
            runTest {
                assertEquals(
                    listOf(earlGrey, americano),
                    search(ProductSearchFilter(ids = setOf(americano, earlGrey, ProductId(99)))).ids(),
                )
            }

        @Test
        fun `다른 조건과 함께 주면 둘 다 만족하는 상품만 남는다`() =
            runTest {
                assertEquals(
                    listOf(americano),
                    search(ProductSearchFilter(ids = setOf(americano, latte), status = ProductStatus.ACTIVE)).ids(),
                )
            }

        @Test
        fun `빈 ids는 아무것도 돌려주지 않는다`() =
            runTest {
                val page = search(ProductSearchFilter(ids = emptySet()))

                assertEquals(emptyList(), page.ids())
                assertEquals(0, page.totalElements)
            }

        @Test
        fun `100개를 넘는 ids는 호출 코드 오류로 거부한다`() {
            assertFailsWith<IllegalArgumentException> {
                ProductSearchFilter(ids = (1L..101L).map(::ProductId).toSet())
            }
        }
    }

    @Nested
    @DisplayName("페이징")
    inner class Paging {
        @Test
        fun `첫 페이지는 size만큼 최근 등록 순으로 담고 전체 건수와 페이지 수를 함께 준다`() =
            runTest {
                val page = service.search(ProductSearchFilter(), PageRequest(page = 0, size = 4))

                assertEquals(listOf(coldBrew, icedAmericano, tumbler, earlGrey), page.ids())
                assertEquals(0, page.page)
                assertEquals(4, page.size)
                assertEquals(6, page.totalElements)
                assertEquals(2, page.totalPages)
            }

        @Test
        fun `마지막 페이지는 남은 상품만 담는다`() =
            runTest {
                val page = service.search(ProductSearchFilter(), PageRequest(page = 1, size = 4))

                assertEquals(listOf(latte, americano), page.ids())
                assertEquals(6, page.totalElements)
            }

        @Test
        fun `마지막 페이지를 지나면 내용은 비고 전체 건수는 그대로다`() =
            runTest {
                val page = service.search(ProductSearchFilter(), PageRequest(page = 2, size = 4))

                assertEquals(emptyList(), page.ids())
                assertEquals(6, page.totalElements)
                assertEquals(2, page.totalPages)
            }

        @Test
        fun `전체 건수는 조건에 맞는 상품만 센다`() =
            runTest {
                val page = service.search(ProductSearchFilter(categoryId = coffee), PageRequest(page = 0, size = 2))

                assertEquals(listOf(coldBrew, icedAmericano), page.ids())
                assertEquals(4, page.totalElements)
                assertEquals(2, page.totalPages)
            }

        @Test
        fun `맞는 상품이 없으면 전체 건수와 페이지 수가 0이다`() =
            runTest {
                val page = search(ProductSearchFilter(keyword = "없는 상품"))

                assertEquals(0, page.totalElements)
                assertEquals(0, page.totalPages)
            }
    }

    private suspend fun search(filter: ProductSearchFilter): Page<Product> = service.search(filter, PageRequest(page = 0, size = 20))

    private fun Page<Product>.ids(): List<ProductId> = content.map { it.id }

    private suspend fun insertProduct(
        name: String,
        sku: String,
        categoryId: Long,
        status: String,
    ) = execute(
        """
        INSERT INTO products (sku, name, category_id, base_price, status, store_scope, tracks_inventory)
        VALUES ('$sku', '$name', $categoryId, 4500, '$status', 'ALL', false)
        """.trimIndent(),
    )
}
