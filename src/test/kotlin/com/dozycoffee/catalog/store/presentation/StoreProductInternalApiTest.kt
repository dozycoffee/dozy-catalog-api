package com.dozycoffee.catalog.store.presentation

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.WithDozyPrincipal
import com.dozycoffee.catalog.support.ApiTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Store 서비스가 부르는 매장 API(docs/api/store.md). 규칙 자체는 유스케이스 테스트가 덮으므로 응답 형식, 오류의 상태 매핑,
// 내부 응답에 본사 내부 정보가 없는지를 확인한다. 준비 데이터는 SQL로 직접 넣는다(docs/architecture/testing.md).
@DisplayName("매장 내부 API (Store 서비스)")
@WithDozyPrincipal(type = PrincipalType.SYSTEM, roles = ["catalog:store_agent"])
class StoreProductInternalApiTest : ApiTest() {
    private val base = "/api/v1/internal/stores/$GANGNAM/products"

    // 1 아메리카노, 2 카페라떼(재고 미추적), 3 텀블러(재고 추적), 4 홍대 한정 상품(강남은 판매 범위 밖)
    @BeforeEach
    fun prepareProducts() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            insertProduct("아메리카노", tracksInventory = false, imageUrl = "https://cdn.example.com/p/1.png")
            insertProduct("카페라떼", tracksInventory = false)
            insertProduct("텀블러", tracksInventory = true)
            insertProduct("홍대 한정 상품", tracksInventory = false, storeScope = "LIMITED")
            execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (4, $HONGDAE)")
            execute("INSERT INTO product_groups (name) VALUES ('여름 시즌')")
            execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (1, 1)")
        }

    @Nested
    @DisplayName("GET 매장 상품 목록")
    inner class ListProducts {
        @Test
        fun `상품 기준 정보와 이 매장의 상태를 담고 본사 내부 정보는 담지 않는다`() {
            client
                .get()
                .uri("$base?ids=1")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].productId")
                .isEqualTo(1)
                .jsonPath("$[0].sku")
                .isEqualTo("DZ-00000001")
                .jsonPath("$[0].name")
                .isEqualTo("아메리카노")
                .jsonPath("$[0].categoryId")
                .isEqualTo(2)
                .jsonPath("$[0].imageUrl")
                .isEqualTo("https://cdn.example.com/p/1.png")
                .jsonPath("$[0].basePrice")
                .isEqualTo(4500)
                .jsonPath("$[0].tracksInventory")
                .isEqualTo(false)
                .jsonPath("$[0].visibility")
                .isEqualTo("VISIBLE")
                .jsonPath("$[0].stockStatus")
                .isEqualTo("ON_SALE")
                .jsonPath("$[0].status")
                .doesNotExist()
                .jsonPath("$[0].storeScope")
                .doesNotExist()
                .jsonPath("$[0].targetStoreIds")
                .doesNotExist()
                .jsonPath("$[0].groupIds")
                .doesNotExist()
                .jsonPath("$[0].version")
                .doesNotExist()

            val body = bodyOf(client.get().uri("$base?ids=1").exchange())
            // jsonPath는 null 값과 없는 필드를 구분하지 못해 본문에서 직접 확인한다.
            assertTrue(body.contains("\"displayOrder\":null"), body)
        }

        @Test
        fun `숨긴 상품도 목록에 있고 판매중·품절은 null이다`() =
            runTest {
                execute("INSERT INTO store_display_settings (store_id, product_id, visibility) VALUES ($GANGNAM, 1, 'HIDDEN')")

                val body = bodyOf(client.get().uri("$base?ids=1").exchange())

                assertTrue(body.contains("\"visibility\":\"HIDDEN\""), body)
                assertTrue(body.contains("\"stockStatus\":null"), body)
            }

        @Test
        fun `재고 추적 상품은 재고 정보를 받은 적이 없으면 품절이다`() {
            client
                .get()
                .uri("$base?ids=3")
                .exchange()
                .expectBody()
                .jsonPath("$[0].tracksInventory")
                .isEqualTo(true)
                .jsonPath("$[0].stockStatus")
                .isEqualTo("SOLD_OUT")
        }

        @Test
        fun `진열 순서를 정한 상품이 앞에 오고 정하지 않은 상품은 뒤에 등록 순으로 온다`() =
            runTest {
                execute("INSERT INTO store_display_settings (store_id, product_id, display_order) VALUES ($GANGNAM, 3, 1)")
                execute("INSERT INTO store_display_settings (store_id, product_id, display_order) VALUES ($GANGNAM, 2, 2)")

                client
                    .get()
                    .uri(base)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    // 판매 범위 밖인 4번 상품은 목록에 없다.
                    .jsonPath("$[*].productId")
                    .isEqualTo(listOf(3, 2, 1))
                    .jsonPath("$[*].displayOrder")
                    .isEqualTo(listOf(1, 2, null))
            }

        @Test
        fun `ids로 거르면 판매 범위 밖 상품과 없는 ID는 빠진다`() {
            client
                .get()
                .uri("$base?ids=1,4,99")
                .exchange()
                .expectBody()
                .jsonPath("$[*].productId")
                .isEqualTo(listOf(1))
        }
    }

    @Nested
    @DisplayName("PUT 진열 순서 일괄 변경")
    inner class ReplaceDisplayOrder {
        @Test
        fun `변경 후 목록 전체를 돌려준다`() {
            putJson("$base/display-order", """{"productIds": [2, 1]}""")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[*].productId")
                .isEqualTo(listOf(2, 1, 3))
                .jsonPath("$[*].displayOrder")
                .isEqualTo(listOf(1, 2, null))
        }

        @Test
        fun `같은 상품이 두 번 있으면 400 DUPLICATE_DISPLAY_ORDER_PRODUCT`() =
            runTest {
                putJson("$base/display-order", """{"productIds": [1, 2, 1]}""")
                    .expectProblem(400, "DUPLICATE_DISPLAY_ORDER_PRODUCT")

                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `이 매장에서 판매할 수 없는 상품이 있으면 404 PRODUCT_NOT_FOUND이고 아무것도 바꾸지 않는다`() =
            runTest {
                putJson("$base/display-order", """{"productIds": [1, 4]}""")
                    .expectProblem(404, "PRODUCT_NOT_FOUND")

                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
            }
    }

    @Nested
    @DisplayName("PUT 숨김·노출 전환")
    inner class ChangeVisibility {
        @Test
        fun `그 상품의 매장 상품을 돌려준다`() {
            val body =
                bodyOf(
                    putJson("$base/1/visibility", """{"visibility": "HIDDEN"}""")
                        .expectStatus()
                        .isOk,
                )

            assertTrue(body.contains("\"productId\":1"), body)
            assertTrue(body.contains("\"visibility\":\"HIDDEN\""), body)
            assertTrue(body.contains("\"stockStatus\":null"), body)
            assertTrue(!body.contains("\"groupIds\""), body)
        }

        @Test
        fun `다시 노출로 되돌리면 판매중·품절이 다시 나온다`() =
            runTest {
                execute("INSERT INTO store_display_settings (store_id, product_id, visibility) VALUES ($GANGNAM, 1, 'HIDDEN')")

                putJson("$base/1/visibility", """{"visibility": "VISIBLE"}""")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.visibility")
                    .isEqualTo("VISIBLE")
                    .jsonPath("$.stockStatus")
                    .isEqualTo("ON_SALE")
            }

        @Test
        fun `판매 범위 밖 상품이면 404 PRODUCT_NOT_FOUND`() {
            putJson("$base/4/visibility", """{"visibility": "HIDDEN"}""")
                .expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("PUT 수동 품절")
    inner class ChangeStockStatus {
        @Test
        fun `그 상품의 매장 상품을 돌려준다`() {
            putJson("$base/2/stock-status", """{"stockStatus": "SOLD_OUT"}""")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.productId")
                .isEqualTo(2)
                .jsonPath("$.visibility")
                .isEqualTo("VISIBLE")
                .jsonPath("$.stockStatus")
                .isEqualTo("SOLD_OUT")
        }

        @Test
        fun `재고 추적 상품이면 422 STOCK_STATUS_NOT_MANUALLY_EDITABLE`() =
            runTest {
                putJson("$base/3/stock-status", """{"stockStatus": "ON_SALE"}""")
                    .expectProblem(422, "STOCK_STATUS_NOT_MANUALLY_EDITABLE")

                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }

        @Test
        fun `판매 범위 밖 상품이면 404 PRODUCT_NOT_FOUND`() {
            putJson("$base/4/stock-status", """{"stockStatus": "SOLD_OUT"}""")
                .expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("GET 유효 옵션 구성")
    inner class EffectiveOptions {
        @BeforeEach
        fun linkOptionGroup() =
            runTest {
                execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('사이즈', 'SINGLE', true)")
                execute(
                    "INSERT INTO options (option_group_id, option_key, name, price, display_order) VALUES " +
                        "(1, 'REGULAR', '레귤러', 0, 0), (1, 'LARGE', '라지', 500, 1)",
                )
                execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 1, 0)")
                execute(
                    "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price) " +
                        "VALUES (1, 1, 'LARGE', 'PRICE', 700)",
                )
            }

        @Test
        fun `본사 API와 같은 형식으로 상품별 예외를 반영한 옵션과 표시용 시작가를 돌려준다`() {
            val body =
                bodyOf(
                    client
                        .get()
                        .uri("$base/1/effective-options")
                        .exchange()
                        .expectStatus()
                        .isOk,
                )
            assertTrue(body.contains("\"autoSelectedOptionKey\":null"), body)

            client
                .get()
                .uri("$base/1/effective-options")
                .exchange()
                .expectBody()
                .jsonPath("$.productId")
                .isEqualTo(1)
                .jsonPath("$.basePrice")
                .isEqualTo(4500)
                .jsonPath("$.displayStartingPrice")
                .isEqualTo(4500)
                .jsonPath("$.groups[0].optionGroupId")
                .isEqualTo(1)
                .jsonPath("$.groups[0].name")
                .isEqualTo("사이즈")
                .jsonPath("$.groups[0].selectionType")
                .isEqualTo("SINGLE")
                .jsonPath("$.groups[0].required")
                .isEqualTo(true)
                .jsonPath("$.groups[0].options[*].optionKey")
                .isEqualTo(listOf("REGULAR", "LARGE"))
                .jsonPath("$.groups[0].options[1].name")
                .isEqualTo("라지")
                .jsonPath("$.groups[0].options[1].price")
                .isEqualTo(700)
                .jsonPath("$.groups[0].options[1].priceOverridden")
                .isEqualTo(true)
        }

        @Test
        fun `판매 범위 밖 상품이면 404 PRODUCT_NOT_FOUND`() {
            client
                .get()
                .uri("$base/4/effective-options")
                .exchange()
                .expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        @Test
        fun `Store 서비스는 본사의 매장 상품 조회를 부를 수 없다`() {
            client
                .get()
                .uri("/api/v1/admin/stores/$GANGNAM/products")
                .exchange()
                .expectProblem(403, "FORBIDDEN")
        }
    }

    private fun putJson(
        uri: String,
        json: String,
    ): WebTestClient.ResponseSpec =
        client
            .put()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()

    private fun WebTestClient.ResponseSpec.expectProblem(
        status: Int,
        code: String,
    ): WebTestClient.BodyContentSpec =
        expectStatus()
            .isEqualTo(status)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo(code)

    private fun bodyOf(response: WebTestClient.ResponseSpec): String =
        String(assertNotNull(response.expectBody().returnResult().responseBody))

    private suspend fun insertProduct(
        name: String,
        tracksInventory: Boolean,
        storeScope: String = "ALL",
        imageUrl: String? = null,
    ) {
        val id = count("SELECT count(*) FROM products") + 1
        val image = imageUrl?.let { "'$it'" } ?: "NULL"
        execute(
            """
            INSERT INTO products (sku, name, category_id, image_url, base_price, status, store_scope, tracks_inventory)
            VALUES ('DZ-${id.toString().padStart(8, '0')}', '$name', 2, $image, 4500, 'ACTIVE', '$storeScope', $tracksInventory)
            """.trimIndent(),
        )
    }

    private companion object {
        const val GANGNAM = 10L
        const val HONGDAE = 20L
    }
}
