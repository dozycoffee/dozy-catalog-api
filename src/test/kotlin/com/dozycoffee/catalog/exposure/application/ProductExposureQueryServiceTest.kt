package com.dozycoffee.catalog.exposure.application

import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.store.application.policy.ProductVisibilityPolicy
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.support.ApplicationTest
import com.dozycoffee.catalog.support.FakeStoreDirectory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 검증 대상은 조회뿐이라 상품·진열 설정·판매 가능 여부 같은 준비 데이터는 다른 유스케이스를 거치지 않고
// SQL로 직접 넣는다(docs/architecture/testing.md). 전체 매장은 Store BC의 값이라 FakeStoreDirectory로 정한다.
@DisplayName("A7. 상품 노출 현황 조회 (요구사항 1.10, 3장)")
class ProductExposureQueryServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ProductExposureQueryService

    @Autowired
    private lateinit var storeDirectory: FakeStoreDirectory

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var displaySettingRepository: StoreDisplaySettingRepository

    @Autowired
    private lateinit var availabilityRepository: StoreProductAvailabilityRepository

    private val gangnam = StoreId(10)
    private val hongdae = StoreId(20)
    private val busan = StoreId(30)

    private val americano = ProductId(1)
    private val tumbler = ProductId(2)
    private val seasonal = ProductId(3)

    private val beverage = CategoryId(1)
    private val coffee = CategoryId(2)
    private val md = CategoryId(3)

    private val newMenuTag = TagId(1)
    private val seasonGroup = ProductGroupId(1)

    @BeforeEach
    fun prepareCatalog() =
        runTest {
            storeDirectory.replaceAll(listOf(gangnam, hongdae, busan))
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO categories (name) VALUES ('MD')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('텀블러', 3)")
            execute("INSERT INTO tags (name) VALUES ('신메뉴')")
            execute("INSERT INTO product_groups (name) VALUES ('시즌 운영')")

            insertProduct("아메리카노", categoryId = 2, status = "ACTIVE", tracksInventory = false)
            insertProduct("텀블러", categoryId = 4, status = "ACTIVE", tracksInventory = true)
            insertProduct("시즌 라떼", categoryId = 2, status = "ACTIVE", tracksInventory = false, storeScope = "LIMITED")
            addTargetStores(seasonal, listOf(gangnam, hongdae))
            execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, 1), (3, 1)")
            execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (3, 1)")
        }

    @Nested
    @DisplayName("요약 조회")
    inner class Summary {
        @Test
        fun `판매 범위가 전체면 판매 가능 매장 수는 전체 매장 수다`() =
            runTest {
                val summary = summaryOf(americano)

                assertEquals(3, summary.sellableStoreCount)
                assertEquals(3, summary.exposedStoreCount)
            }

        @Test
        fun `판매 범위가 한정이면 판매 가능 매장 수는 대상 매장 수다`() =
            runTest {
                val summary = summaryOf(seasonal)

                assertEquals(2, summary.sellableStoreCount)
                assertEquals(2, summary.exposedStoreCount)
            }

        @Test
        fun `점주가 숨긴 매장은 노출 중으로 세지 않는다`() =
            runTest {
                hide(gangnam, americano)

                val summary = summaryOf(americano)

                assertEquals(3, summary.sellableStoreCount)
                assertEquals(2, summary.exposedStoreCount)
            }

        @Test
        fun `품절이어도 숨기지 않았으면 노출 중으로 센다`() =
            runTest {
                markSoldOutByOwner(gangnam, americano)

                // 재고 정보를 받은 적 없는 재고 추적 상품(텀블러)도 품절이지만 노출 중이다.
                assertEquals(3, summaryOf(americano).exposedStoreCount)
                assertEquals(3, summaryOf(tumbler).exposedStoreCount)
            }

        @Test
        fun `Active가 아닌 상품은 노출 중인 매장이 없다`() =
            runTest {
                insertProduct("신메뉴 후보", categoryId = 2, status = "DRAFT", tracksInventory = false)
                insertProduct("단종된 상품", categoryId = 2, status = "DISCONTINUED", tracksInventory = false)

                // 판매 가능 매장 수는 판매 범위만 보므로 상태와 무관하게 그대로다.
                assertEquals(3, summaryOf(ProductId(4)).sellableStoreCount)
                assertEquals(0, summaryOf(ProductId(4)).exposedStoreCount)
                assertEquals(0, summaryOf(ProductId(5)).exposedStoreCount)
            }

        @Test
        fun `대상 매장이 비어 있는 한정 판매 상품은 판매 가능 매장이 없다`() =
            runTest {
                insertProduct("미배정 상품", categoryId = 2, status = "ACTIVE", tracksInventory = false, storeScope = "LIMITED")

                val summary = summaryOf(ProductId(4))

                assertEquals(0, summary.sellableStoreCount)
                assertEquals(0, summary.exposedStoreCount)
            }

        @Test
        fun `판매 범위 밖 매장의 숨김 설정은 수치에 영향을 주지 않는다`() =
            runTest {
                hide(busan, seasonal)

                val summary = summaryOf(seasonal)

                assertEquals(2, summary.sellableStoreCount)
                assertEquals(2, summary.exposedStoreCount)
            }

        @Test
        fun `상품 정보와 함께 상품 id 순으로 나온다`() =
            runTest {
                val summaries = service.summarize().content

                assertEquals(listOf(americano, tumbler, seasonal), summaries.map { it.productId })
                assertEquals("아메리카노", summaries.first().name)
            }
    }

    @Nested
    @DisplayName("필터")
    inner class Filter {
        @Test
        fun `소분류로 거르면 그 소분류의 상품만 나온다`() =
            runTest {
                val summaries = service.summarize(ProductExposureFilter(categoryId = coffee)).content

                assertEquals(listOf(americano, seasonal), summaries.map { it.productId })
            }

        @Test
        fun `대분류로 거르면 그 아래 소분류의 상품이 모두 나온다`() =
            runTest {
                assertEquals(
                    listOf(americano, seasonal),
                    service.summarize(ProductExposureFilter(categoryId = beverage)).content.map { it.productId },
                )
                assertEquals(
                    listOf(tumbler),
                    service.summarize(ProductExposureFilter(categoryId = md)).content.map { it.productId },
                )
            }

        @Test
        fun `태그로 거른다`() =
            runTest {
                val summaries = service.summarize(ProductExposureFilter(tagId = newMenuTag)).content

                assertEquals(listOf(americano, seasonal), summaries.map { it.productId })
            }

        @Test
        fun `그룹으로 거른다`() =
            runTest {
                val summaries = service.summarize(ProductExposureFilter(groupId = seasonGroup)).content

                assertEquals(listOf(seasonal), summaries.map { it.productId })
            }

        @Test
        fun `상품 ID로 거른다`() =
            runTest {
                val summaries = service.summarize(ProductExposureFilter(ids = setOf(seasonal, americano, ProductId(999))))

                assertEquals(listOf(americano, seasonal), summaries.content.map { it.productId })
                assertEquals(2, summaries.totalElements)
            }

        @Test
        fun `상품 ID를 다른 조건과 함께 주면 모두 만족하는 상품만 남는다`() =
            runTest {
                val filter = ProductExposureFilter(ids = setOf(americano, tumbler, seasonal), tagId = newMenuTag, groupId = seasonGroup)

                assertEquals(listOf(seasonal), service.summarize(filter).content.map { it.productId })
            }

        @Test
        fun `상품 ID가 빈 집합이면 빈 페이지다`() =
            runTest {
                val page = service.summarize(ProductExposureFilter(ids = emptySet()))

                assertEquals(emptyList(), page.content)
                assertEquals(0, page.totalElements)
            }

        @Test
        fun `상품 ID는 100개를 넘을 수 없다`() {
            assertFailsWith<IllegalArgumentException> {
                ProductExposureFilter(ids = (1L..101L).map(::ProductId).toSet())
            }
        }

        @Test
        fun `여러 조건을 함께 주면 모두 만족하는 상품만 남는다`() =
            runTest {
                val matched = ProductExposureFilter(categoryId = coffee, tagId = newMenuTag, groupId = seasonGroup)
                val unmatched = ProductExposureFilter(categoryId = md, tagId = newMenuTag)

                assertEquals(listOf(seasonal), service.summarize(matched).content.map { it.productId })
                assertEquals(emptyList(), service.summarize(unmatched).content.map { it.productId })
            }
    }

    @Nested
    @DisplayName("페이징")
    inner class Paging {
        @Test
        fun `상품 등록 순으로 페이지를 자르고 전체 건수를 함께 준다`() =
            runTest {
                val first = service.summarize(pageRequest = PageRequest(page = 0, size = 2))
                val last = service.summarize(pageRequest = PageRequest(page = 1, size = 2))

                assertEquals(listOf(americano, tumbler), first.content.map { it.productId })
                assertEquals(listOf(seasonal), last.content.map { it.productId })
                listOf(first, last).forEach { page ->
                    assertEquals(3, page.totalElements)
                    assertEquals(2, page.totalPages)
                    assertEquals(2, page.size)
                }
                assertEquals(0, first.page)
                assertEquals(1, last.page)
            }

        @Test
        fun `마지막 페이지를 지나면 내용은 비고 전체 건수는 그대로다`() =
            runTest {
                val page = service.summarize(pageRequest = PageRequest(page = 2, size = 2))

                assertEquals(emptyList(), page.content)
                assertEquals(3, page.totalElements)
                assertEquals(2, page.page)
            }

        @Test
        fun `필터를 준 전체 건수는 조건에 맞는 상품 수다`() =
            runTest {
                val page = service.summarize(ProductExposureFilter(categoryId = coffee), PageRequest(page = 0, size = 1))

                assertEquals(listOf(americano), page.content.map { it.productId })
                assertEquals(2, page.totalElements)
                assertEquals(2, page.totalPages)
            }

        @Test
        fun `페이지 수치는 다른 페이지 상품의 설정과 무관하다`() =
            runTest {
                hide(gangnam, americano)
                hide(hongdae, seasonal)

                val last = service.summarize(pageRequest = PageRequest(page = 1, size = 2)).content.single()

                assertEquals(seasonal, last.productId)
                assertEquals(2, last.sellableStoreCount)
                assertEquals(1, last.exposedStoreCount)
            }
    }

    @Nested
    @DisplayName("상세 조회")
    inner class Detail {
        @Test
        fun `매장별 노출 상태를 매장 id 순으로 돌려준다`() =
            runTest {
                hide(hongdae, americano)

                val detail = service.findDetail(americano)

                assertEquals(listOf(gangnam, hongdae, busan), detail.stores.map { it.storeId })
                assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), detail.stores[0].visibility)
                assertEquals(StoreVisibility.NotVisible, detail.stores[1].visibility)
                assertEquals(3, detail.summary.sellableStoreCount)
                assertEquals(2, detail.summary.exposedStoreCount)
            }

        @Test
        fun `한정 판매 상품은 대상 매장만 나온다`() =
            runTest {
                val detail = service.findDetail(seasonal)

                assertEquals(listOf(gangnam, hongdae), detail.stores.map { it.storeId })
            }

        @Test
        fun `재고 추적 상품은 재고 정보를 받은 적 없으면 품절로 노출된다`() =
            runTest {
                val detail = service.findDetail(tumbler)

                assertEquals(
                    List(3) { StoreVisibility.Visible(StockStatus.SOLD_OUT) },
                    detail.stores.map { it.visibility },
                )
                assertEquals(3, detail.summary.exposedStoreCount)
            }

        @Test
        fun `없는 상품을 조회하면 거부된다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> { service.findDetail(ProductId(999)) }
            }
    }

    @Nested
    @DisplayName("노출 판단 일치")
    inner class SameJudgement {
        // 이 조회는 점주 화면과 다른 경로로 노출을 판단한다. 같은 데이터에서 두 결과가 갈라지면
        // 본사와 점주가 서로 다른 현황을 보게 되므로, 조합을 돌며 ProductVisibilityPolicy의 답과 맞춘다.
        @Test
        fun `상태·판매 범위·숨김·품절 조합에서 정책과 같은 판단을 낸다`() =
            runTest {
                insertProduct("신메뉴 후보", categoryId = 2, status = "DRAFT", tracksInventory = false)
                insertProduct("단종된 상품", categoryId = 2, status = "DISCONTINUED", tracksInventory = false)
                val draft = ProductId(4)
                val discontinued = ProductId(5)
                hide(gangnam, americano)
                markSoldOutByOwner(hongdae, americano)
                markStockByInventory(gangnam, tumbler, StockStatus.ON_SALE)
                hide(busan, tumbler)
                hide(hongdae, seasonal)
                hide(busan, seasonal)
                hide(gangnam, discontinued)

                listOf(americano, tumbler, seasonal, draft, discontinued).forEach { productId ->
                    val detail = service.findDetail(productId)
                    val byStore = detail.stores.associate { it.storeId to it.visibility }
                    listOf(gangnam, hongdae, busan).forEach { storeId ->
                        assertEquals(
                            policyVisibility(productId, storeId),
                            // 상세 목록에 없는 매장은 판매 범위 밖이라 비노출이다.
                            byStore[storeId] ?: StoreVisibility.NotVisible,
                            "상품 ${productId.value} / 매장 ${storeId.value}",
                        )
                    }
                    assertEquals(
                        detail.stores.count { it.visibility is StoreVisibility.Visible },
                        summaryOf(productId).exposedStoreCount,
                    )
                }
            }

        // 요약은 페이지 단위로 상품과 매장 설정을 읽는다. 페이지를 어떻게 잘라도 각 상품의 수치가 상세 조회와 같아야 한다.
        @Test
        fun `페이지마다 요약 수치가 상세 조회와 같다`() =
            runTest {
                insertProduct("신메뉴 후보", categoryId = 2, status = "DRAFT", tracksInventory = false)
                insertProduct("단종된 상품", categoryId = 2, status = "DISCONTINUED", tracksInventory = false)
                hide(gangnam, americano)
                markSoldOutByOwner(hongdae, americano)
                markStockByInventory(gangnam, tumbler, StockStatus.ON_SALE)
                hide(busan, tumbler)
                hide(hongdae, seasonal)
                hide(gangnam, ProductId(5))

                listOf(1, 2, 3).forEach { size ->
                    var page = 0
                    val seen = mutableListOf<ProductId>()
                    do {
                        val summaries = service.summarize(pageRequest = PageRequest(page = page, size = size))
                        summaries.content.forEach { summary ->
                            assertEquals(
                                service.findDetail(summary.productId).summary,
                                summary,
                                "size $size / 상품 ${summary.productId.value}",
                            )
                        }
                        seen += summaries.content.map { it.productId }
                        page++
                    } while (page < summaries.totalPages)
                    assertEquals((1L..5L).map(::ProductId), seen, "size $size")
                    assertEquals(seen.size.toLong(), service.summarize().totalElements)
                }
            }

        private suspend fun policyVisibility(
            productId: ProductId,
            storeId: StoreId,
        ): StoreVisibility =
            tx {
                ProductVisibilityPolicy.resolve(
                    product = checkNotNull(productRepository.findById(productId)),
                    storeId = storeId,
                    displaySetting = displaySettingRepository.findByStoreAndProduct(storeId, productId),
                    availability = availabilityRepository.findById(StoreProductAvailabilityId(storeId, productId)),
                )
            }
    }

    @Nested
    @DisplayName("경계")
    inner class Boundary {
        @Test
        fun `상품이 하나도 없으면 빈 목록이다`() =
            runTest {
                execute("DELETE FROM products")

                val page = service.summarize()

                assertEquals(emptyList(), page.content)
                assertEquals(0, page.totalElements)
            }

        @Test
        fun `매장이 하나도 없으면 전체 판매 상품의 판매 가능 매장이 없다`() =
            runTest {
                storeDirectory.replaceAll(emptyList())

                assertEquals(0, summaryOf(americano).sellableStoreCount)
                assertEquals(emptyList(), service.findDetail(americano).stores)
                // 한정 판매 상품의 대상 매장은 Catalog가 가진 값이라 그대로다.
                assertEquals(2, summaryOf(seasonal).sellableStoreCount)
            }
    }

    private suspend fun summaryOf(productId: ProductId): ProductExposureSummary =
        service.summarize(ProductExposureFilter(ids = setOf(productId))).content.single()

    private suspend fun insertProduct(
        name: String,
        categoryId: Long,
        status: String,
        tracksInventory: Boolean,
        storeScope: String = "ALL",
    ) = execute(
        """
        INSERT INTO products (name, category_id, base_price, status, store_scope, tracks_inventory)
        VALUES ('$name', $categoryId, 4500, '$status', '$storeScope', $tracksInventory)
        """.trimIndent(),
    )

    private suspend fun addTargetStores(
        productId: ProductId,
        storeIds: List<StoreId>,
    ) = execute(
        """
        INSERT INTO product_target_stores (product_id, store_id)
        VALUES ${storeIds.joinToString { "(${productId.value}, ${it.value})" }}
        """.trimIndent(),
    )

    private suspend fun hide(
        storeId: StoreId,
        productId: ProductId,
    ) = execute(
        """
        INSERT INTO store_display_settings (store_id, product_id, visibility)
        VALUES (${storeId.value}, ${productId.value}, 'HIDDEN')
        """.trimIndent(),
    )

    private suspend fun markSoldOutByOwner(
        storeId: StoreId,
        productId: ProductId,
    ) = execute(
        """
        INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status)
        VALUES (${storeId.value}, ${productId.value}, 'OWNER', 'SOLD_OUT')
        """.trimIndent(),
    )

    private suspend fun markStockByInventory(
        storeId: StoreId,
        productId: ProductId,
        stockStatus: StockStatus,
    ) = execute(
        """
        INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status, last_event_at)
        VALUES (${storeId.value}, ${productId.value}, 'INVENTORY', '$stockStatus', TIMESTAMPTZ '2026-09-20 00:00:00+09')
        """.trimIndent(),
    )
}
