package com.dozycoffee.catalog.exposure.presentation

import com.dozycoffee.catalog.common.web.PageResponse
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.exposure.presentation.dto.ProductExposureDetailResponse
import com.dozycoffee.catalog.exposure.presentation.dto.ProductExposureResponse
import com.dozycoffee.catalog.support.ApiTest
import com.dozycoffee.catalog.support.FakeStoreDirectory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 노출 현황 API(docs/api/exposure.md)의 응답 형식, 페이징, 필터. 노출 판단 규칙 자체는 ProductExposureQueryServiceTest가 덮는다.
// 준비 데이터는 검증 대상이 아니므로 SQL로 직접 넣고, 전체 매장은 FakeStoreDirectory로 정한다.
@DisplayName("노출 현황 API (요구사항 1.10, 3장)")
class ProductExposureApiTest : ApiTest() {
    @Autowired
    private lateinit var storeDirectory: FakeStoreDirectory

    @BeforeEach
    fun prepareCatalog() =
        runTest {
            storeDirectory.replaceAll(listOf(StoreId(101), StoreId(102), StoreId(103)))
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO categories (name) VALUES ('MD')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('텀블러', 3)")
            execute("INSERT INTO tags (name) VALUES ('신메뉴')")
            execute("INSERT INTO product_groups (name) VALUES ('시즌 운영')")

            // 1: 아메리카노(전체 판매), 2: 텀블러(재고 추적), 3: 시즌 라떼(101·102 한정), 4: 신메뉴 후보(Draft, SKU 없는 과거 데이터)
            insertProduct("DZ-00000001", "아메리카노", categoryId = 2, status = "ACTIVE", tracksInventory = false)
            insertProduct("DZ-00000002", "텀블러", categoryId = 4, status = "ACTIVE", tracksInventory = true)
            insertProduct("DZ-00000003", "시즌 라떼", categoryId = 2, status = "ACTIVE", tracksInventory = false, storeScope = "LIMITED")
            insertProduct(null, "신메뉴 후보", categoryId = 2, status = "DRAFT", tracksInventory = false)
            execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (3, 101), (3, 102)")
            execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, 1), (3, 1)")
            execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (3, 1)")

            // 아메리카노는 102에서 숨김, 101에서 점주 품절. 텀블러는 101에만 재고가 들어왔다.
            execute("INSERT INTO store_display_settings (store_id, product_id, visibility) VALUES (102, 1, 'HIDDEN')")
            execute(
                "INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status) VALUES (101, 1, 'OWNER', 'SOLD_OUT')",
            )
            execute(
                """
                INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status, last_event_at)
                VALUES (101, 2, 'INVENTORY', 'ON_SALE', TIMESTAMPTZ '2026-09-20 00:00:00+09')
                """.trimIndent(),
            )
        }

    private fun get(uri: String): WebTestClient.ResponseSpec = client.get().uri(uri).exchange()

    private fun summaries(uri: String): PageResponse<ProductExposureResponse> =
        assertNotNull(
            get(uri)
                .expectStatus()
                .isOk
                .expectBody<PageResponse<ProductExposureResponse>>()
                .returnResult()
                .responseBody,
        )

    private fun productIds(uri: String): List<Long> = summaries(uri).content.map { it.productId }

    @Nested
    @DisplayName("GET /product-exposures")
    inner class Summarize {
        @Test
        fun `상품별 판매 가능 매장 수와 노출 중인 매장 수를 상품 정보와 함께 준다`() {
            get("/api/v1/admin/product-exposures")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content[0].productId")
                .isEqualTo(1)
                .jsonPath("$.content[0].sku")
                .isEqualTo("DZ-00000001")
                .jsonPath("$.content[0].name")
                .isEqualTo("아메리카노")
                .jsonPath("$.content[0].status")
                .isEqualTo("ACTIVE")
                .jsonPath("$.content[0].sellableStoreCount")
                .isEqualTo(3)
                // 102는 숨김이라 빠지고, 101은 품절이어도 노출로 센다.
                .jsonPath("$.content[0].exposedStoreCount")
                .isEqualTo(2)
                .jsonPath("$.content[2].sellableStoreCount")
                .isEqualTo(2)
                .jsonPath("$.content[3].status")
                .isEqualTo("DRAFT")
                .jsonPath("$.content[3].exposedStoreCount")
                .isEqualTo(0)
        }

        @Test
        fun `SKU가 없는 상품은 sku를 null로 준다`() {
            val body =
                get("/api/v1/admin/product-exposures?ids=4")
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .returnResult()
                    .responseBody

            assertTrue(assertNotNull(body).contains("\"sku\":null"), body)
        }

        @Test
        fun `상품 등록 순으로 페이지를 자르고 전체 건수를 함께 준다`() {
            get("/api/v1/admin/product-exposures?page=1&size=3")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content[*].productId")
                .isEqualTo(listOf(4))
                .jsonPath("$.page")
                .isEqualTo(1)
                .jsonPath("$.size")
                .isEqualTo(3)
                .jsonPath("$.totalElements")
                .isEqualTo(4)
                .jsonPath("$.totalPages")
                .isEqualTo(2)
        }

        @Test
        fun `페이지를 주지 않으면 첫 페이지를 20건씩 준다`() {
            val page = summaries("/api/v1/admin/product-exposures")

            assertEquals(0, page.page)
            assertEquals(20, page.size)
            assertEquals(listOf(1L, 2L, 3L, 4L), page.content.map { it.productId })
        }

        @Test
        fun `상품 ID로 거른다`() {
            assertEquals(listOf(1L, 3L), productIds("/api/v1/admin/product-exposures?ids=3,1,999"))
        }

        @Test
        fun `대분류로 거르면 그 아래 소분류의 상품이 모두 나온다`() {
            assertEquals(listOf(1L, 3L, 4L), productIds("/api/v1/admin/product-exposures?categoryId=1"))
            assertEquals(listOf(2L), productIds("/api/v1/admin/product-exposures?categoryId=4"))
        }

        @Test
        fun `태그로 거른다`() {
            assertEquals(listOf(1L, 3L), productIds("/api/v1/admin/product-exposures?tagId=1"))
        }

        @Test
        fun `상품 그룹으로 거른다`() {
            assertEquals(listOf(3L), productIds("/api/v1/admin/product-exposures?groupId=1"))
        }

        @Test
        fun `여러 조건을 함께 주면 모두 만족하는 상품만 남는다`() {
            assertEquals(listOf(3L), productIds("/api/v1/admin/product-exposures?ids=1,2,3&categoryId=2&tagId=1&groupId=1"))
        }

        @Test
        fun `ids가 100개를 넘으면 거부한다`() {
            val ids = (1..101).joinToString(",")

            get("/api/v1/admin/product-exposures?ids=$ids").expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `필터 값이 숫자가 아니면 거부한다`() {
            get("/api/v1/admin/product-exposures?categoryId=coffee").expectProblem(400, "INVALID_REQUEST")
        }
    }

    @Nested
    @DisplayName("GET /product-exposures/{productId}")
    inner class FindDetail {
        @Test
        fun `판매 가능한 매장마다 노출 판단 결과를 매장 ID 순으로 준다`() {
            get("/api/v1/admin/product-exposures/1")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.productId")
                .isEqualTo(1)
                .jsonPath("$.sku")
                .isEqualTo("DZ-00000001")
                .jsonPath("$.name")
                .isEqualTo("아메리카노")
                .jsonPath("$.status")
                .isEqualTo("ACTIVE")
                .jsonPath("$.sellableStoreCount")
                .isEqualTo(3)
                .jsonPath("$.exposedStoreCount")
                .isEqualTo(2)
                .jsonPath("$.stores[*].storeId")
                .isEqualTo(listOf(101, 102, 103))
                // 101은 점주 품절이지만 노출, 102는 점주가 숨겨 비노출, 103은 설정이 없어 기본값(노출·판매중)이다.
                .jsonPath("$.stores[*].exposed")
                .isEqualTo(listOf(true, false, true))
                .jsonPath("$.stores[0].stockStatus")
                .isEqualTo("SOLD_OUT")
                .jsonPath("$.stores[2].stockStatus")
                .isEqualTo("ON_SALE")
        }

        @Test
        fun `노출되지 않는 매장은 stockStatus를 null로 준다`() {
            val body =
                get("/api/v1/admin/product-exposures/1")
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .returnResult()
                    .responseBody

            // jsonPath는 null 필드와 없는 필드를 구분하지 못해 본문 문자열로 확인한다(docs/architecture/testing.md).
            val store102 = Regex("""\{[^{}]*"storeId":102[^{}]*}""").find(assertNotNull(body))?.value
            assertNotNull(store102, body)
            assertTrue(store102.contains("\"stockStatus\":null"), store102)
        }

        @Test
        fun `한정 판매 상품은 대상 매장만 나온다`() {
            get("/api/v1/admin/product-exposures/3")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.stores[*].storeId")
                .isEqualTo(listOf(101, 102))
        }

        @Test
        fun `재고 추적 상품은 재고가 들어온 매장만 판매중이다`() {
            get("/api/v1/admin/product-exposures/2")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.stores[*].stockStatus")
                .isEqualTo(listOf("ON_SALE", "SOLD_OUT", "SOLD_OUT"))
                .jsonPath("$.exposedStoreCount")
                .isEqualTo(3)
        }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            get("/api/v1/admin/product-exposures/999").expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Test
    fun `요약의 수치는 같은 상품의 상세 수치와 같다`() {
        val summaries = summaries("/api/v1/admin/product-exposures").content

        assertEquals(4, summaries.size)
        summaries.forEach { summary ->
            val detail =
                assertNotNull(
                    get("/api/v1/admin/product-exposures/${summary.productId}")
                        .expectStatus()
                        .isOk
                        .expectBody<ProductExposureDetailResponse>()
                        .returnResult()
                        .responseBody,
                )
            assertEquals(summary.sellableStoreCount, detail.sellableStoreCount, "상품 ${summary.productId}")
            assertEquals(summary.exposedStoreCount, detail.exposedStoreCount, "상품 ${summary.productId}")
            assertEquals(detail.stores.size, detail.sellableStoreCount, "상품 ${summary.productId}")
            assertEquals(detail.stores.count { it.exposed }, detail.exposedStoreCount, "상품 ${summary.productId}")
        }
    }

    private fun WebTestClient.ResponseSpec.expectProblem(
        status: Int,
        code: String,
    ) {
        expectStatus()
            .isEqualTo(status)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo(code)
    }

    private suspend fun insertProduct(
        sku: String?,
        name: String,
        categoryId: Long,
        status: String,
        tracksInventory: Boolean,
        storeScope: String = "ALL",
    ) = execute(
        """
        INSERT INTO products (sku, name, category_id, base_price, status, store_scope, tracks_inventory)
        VALUES (${sku?.let { "'$it'" } ?: "NULL"}, '$name', $categoryId, 4500, '$status', '$storeScope', $tracksInventory)
        """.trimIndent(),
    )
}
