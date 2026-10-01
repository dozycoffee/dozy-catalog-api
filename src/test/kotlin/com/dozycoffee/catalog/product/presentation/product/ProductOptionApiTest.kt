package com.dozycoffee.catalog.product.presentation.product

import com.dozycoffee.catalog.support.ApiTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 상품의 옵션 그룹 연결·순서, 옵션 예외, 유효 옵션 구성 API(docs/api/product.md, 요구사항 1.9).
// 규칙 자체는 ProductOptionApplicationServiceTest가 덮으므로 응답 형식과 오류 매핑의 대표만 확인한다.
// 상품 1은 사이즈(1)만 연결한 버전 0의 Draft 상품이고, 옵션 그룹은 SQL로 넣는다.
@DisplayName("상품 옵션 API")
class ProductOptionApiTest : ApiTest() {
    @BeforeEach
    fun prepare() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute(
                """
                INSERT INTO option_groups (name, selection_type, required)
                VALUES ('사이즈', 'SINGLE', true), ('샷', 'MULTI', false), ('온도', 'SINGLE', true)
                """.trimIndent(),
            )
            execute(
                """
                INSERT INTO options (option_group_id, option_key, name, price, display_order)
                VALUES (1, 'REGULAR', '레귤러', 0, 0), (1, 'LARGE', '라지', 500, 1),
                       (2, 'SHOT', '샷 추가', 500, 0),
                       (3, 'ICE', '아이스', 300, 0)
                """.trimIndent(),
            )
            execute(
                """
                INSERT INTO products (sku, name, category_id, base_price, status, store_scope, tracks_inventory)
                VALUES ('DZ-00000001', '아메리카노', 2, 4500, 'DRAFT', 'ALL', false)
                """.trimIndent(),
            )
            execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 1, 0)")
        }

    @Nested
    @DisplayName("옵션 그룹 연결·해제·순서")
    inner class Links {
        @Test
        fun `연결하면 기존 연결 뒤에 붙이고 새 버전을 돌려준다`() {
            send(post("/option-groups"), "\"0\"", """{"optionGroupId": 2}""")
                .expectStatus()
                .isOk
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"1\"")
                .expectBody()
                .jsonPath("$.optionGroups[*].optionGroupId")
                .isEqualTo(listOf(1, 2))
                .jsonPath("$.version")
                .isEqualTo(1)
        }

        @Test
        fun `이미 연결한 옵션 그룹이면 409 DUPLICATE_OPTION_GROUP_LINK`() {
            send(post("/option-groups"), "\"0\"", """{"optionGroupId": 1}""").expectProblem(409, "DUPLICATE_OPTION_GROUP_LINK")
        }

        @Test
        fun `없는 옵션 그룹이면 404 OPTION_GROUP_NOT_FOUND`() {
            send(post("/option-groups"), "\"0\"", """{"optionGroupId": 999}""").expectProblem(404, "OPTION_GROUP_NOT_FOUND")
        }

        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED`() {
            send(post("/option-groups"), null, """{"optionGroupId": 2}""").expectProblem(428, "VERSION_REQUIRED")
        }

        @Test
        fun `오래된 버전이면 409 VERSION_CONFLICT와 현재 버전을 준다`() =
            runTest {
                execute("UPDATE products SET version = 3 WHERE id = 1")

                send(post("/option-groups"), "\"2\"", """{"optionGroupId": 2}""")
                    .expectProblem(409, "VERSION_CONFLICT")
                    .jsonPath("$.currentVersion")
                    .isEqualTo(3)

                assertEquals(1, count("SELECT count(*) FROM product_option_groups"))
            }

        @Test
        fun `연결을 해제하면 그 연결의 예외도 함께 사라진다`() =
            runTest {
                execute(
                    "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price) VALUES (1, 1, 'LARGE', 'PRICE', 700)",
                )

                send(delete("/option-groups/1"), "\"0\"")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.optionGroups")
                    .isEmpty
                    .jsonPath("$.version")
                    .isEqualTo(1)

                assertEquals(0, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `연결하지 않은 옵션 그룹을 해제하면 404 PRODUCT_OPTION_GROUP_NOT_LINKED`() {
            send(delete("/option-groups/2"), "\"0\"").expectProblem(404, "PRODUCT_OPTION_GROUP_NOT_LINKED")
        }

        @Test
        fun `순서를 바꾸면 그 순서대로 돌려준다`() =
            runTest {
                execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 2, 1)")

                send(put("/option-groups/order"), "\"0\"", """{"optionGroupIds": [2, 1]}""")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.optionGroups[*].optionGroupId")
                    .isEqualTo(listOf(2, 1))
            }

        @Test
        fun `연결된 옵션 그룹이 빠지면 400 INVALID_OPTION_GROUP_ORDER`() =
            runTest {
                execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 2, 1)")

                send(put("/option-groups/order"), "\"0\"", """{"optionGroupIds": [2]}""").expectProblem(400, "INVALID_OPTION_GROUP_ORDER")
            }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            send(client.post().uri("$PRODUCTS/999/option-groups"), "\"0\"", """{"optionGroupId": 2}""")
                .expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("옵션 예외 지정·해제")
    inner class Overrides {
        @Test
        fun `가격 예외를 지정하면 예외를 담은 상품을 돌려준다`() {
            send(put("/option-groups/1/overrides/LARGE"), "\"0\"", """{"type": "PRICE", "price": 700}""")
                .expectStatus()
                .isOk
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"1\"")
                .expectBody()
                .jsonPath("$.optionGroups[0].overrides[0].optionKey")
                .isEqualTo("LARGE")
                .jsonPath("$.optionGroups[0].overrides[0].type")
                .isEqualTo("PRICE")
                .jsonPath("$.optionGroups[0].overrides[0].price")
                .isEqualTo(700)
        }

        @Test
        fun `제외를 지정하면 가격 없는 EXCLUDE 예외로 담는다`() {
            val body =
                send(put("/option-groups/1/overrides/LARGE"), "\"0\"", """{"type": "EXCLUDE"}""")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.optionGroups[0].overrides[0].type")
                    .isEqualTo("EXCLUDE")
                    .returnResult()
                    .responseBody
            assertTrue(String(assertNotNull(body)).contains("\"price\":null"))
        }

        @Test
        fun `같은 옵션 키에 다시 지정하면 기존 예외를 대체한다`() =
            runTest {
                execute(
                    "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price) VALUES (1, 1, 'LARGE', 'PRICE', 700)",
                )

                send(put("/option-groups/1/overrides/LARGE"), "\"0\"", """{"type": "EXCLUDE"}""")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.optionGroups[0].overrides.length()")
                    .isEqualTo(1)
                    .jsonPath("$.optionGroups[0].overrides[0].type")
                    .isEqualTo("EXCLUDE")
            }

        @Test
        fun `제외로 선택 가능한 옵션이 0개가 되면 422 NO_SELECTABLE_OPTION`() =
            runTest {
                execute(
                    "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price) VALUES (1, 1, 'REGULAR', 'EXCLUDE', NULL)",
                )

                send(put("/option-groups/1/overrides/LARGE"), "\"0\"", """{"type": "EXCLUDE"}""").expectProblem(422, "NO_SELECTABLE_OPTION")

                assertEquals(1, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `옵션 그룹에 없는 옵션 키면 404 OPTION_KEY_NOT_FOUND`() {
            send(put("/option-groups/1/overrides/XLARGE"), "\"0\"", """{"type": "EXCLUDE"}""").expectProblem(404, "OPTION_KEY_NOT_FOUND")
        }

        @Test
        fun `연결하지 않은 옵션 그룹이면 404 PRODUCT_OPTION_GROUP_NOT_LINKED`() {
            send(put("/option-groups/2/overrides/SHOT"), "\"0\"", """{"type": "PRICE", "price": 0}""")
                .expectProblem(404, "PRODUCT_OPTION_GROUP_NOT_LINKED")
        }

        @Test
        fun `음수 가격이면 400 INVALID_MONEY_AMOUNT`() {
            send(put("/option-groups/1/overrides/LARGE"), "\"0\"", """{"type": "PRICE", "price": -100}""")
                .expectProblem(400, "INVALID_MONEY_AMOUNT")
        }

        @Test
        fun `PRICE에 가격이 없거나 EXCLUDE에 가격이 있으면 400 INVALID_REQUEST`() {
            send(put("/option-groups/1/overrides/LARGE"), "\"0\"", """{"type": "PRICE"}""").expectProblem(400, "INVALID_REQUEST")
            send(
                put("/option-groups/1/overrides/LARGE"),
                "\"0\"",
                """{"type": "EXCLUDE", "price": 0}""",
            ).expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED`() {
            send(put("/option-groups/1/overrides/LARGE"), null, """{"type": "EXCLUDE"}""").expectProblem(428, "VERSION_REQUIRED")
        }

        @Test
        fun `예외를 해제하면 예외 없는 상품을 돌려준다`() =
            runTest {
                execute(
                    "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price) VALUES (1, 1, 'LARGE', 'PRICE', 700)",
                )

                send(delete("/option-groups/1/overrides/LARGE"), "\"0\"")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.optionGroups[0].overrides")
                    .isEmpty
                    .jsonPath("$.version")
                    .isEqualTo(1)
            }

        @Test
        fun `연결하지 않은 옵션 그룹의 예외를 해제하면 404 PRODUCT_OPTION_GROUP_NOT_LINKED`() {
            send(delete("/option-groups/2/overrides/SHOT"), "\"0\"").expectProblem(404, "PRODUCT_OPTION_GROUP_NOT_LINKED")
        }
    }

    @Nested
    @DisplayName("GET /products/{productId}/effective-options — 유효 옵션 구성")
    inner class EffectiveOptions {
        @Test
        fun `예외를 반영한 옵션 구성과 표시용 시작가를 돌려준다`() =
            runTest {
                execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 2, 1), (1, 3, 2)")
                execute(
                    """
                    INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price)
                    VALUES (1, 1, 'LARGE', 'PRICE', 700)
                    """.trimIndent(),
                )

                val body =
                    client
                        .get()
                        .uri("$PRODUCTS/1/effective-options")
                        .exchange()
                        .expectStatus()
                        .isOk
                        .expectBody()
                        .jsonPath("$.productId")
                        .isEqualTo(1)
                        .jsonPath("$.basePrice")
                        .isEqualTo(4500)
                        // 기준가 4500 + 사이즈 최저가 0 + 온도(필수, 자동 선택) 300. 샷은 선택 그룹이라 더하지 않는다.
                        .jsonPath("$.displayStartingPrice")
                        .isEqualTo(4800)
                        .jsonPath("$.groups[*].optionGroupId")
                        .isEqualTo(listOf(1, 2, 3))
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
                        .jsonPath("$.groups[0].options[0].priceOverridden")
                        .isEqualTo(false)
                        .jsonPath("$.groups[2].autoSelectedOptionKey")
                        .isEqualTo("ICE")
                        .returnResult()
                        .responseBody
                // 자동 선택이 없는 그룹은 null로 나간다.
                assertTrue(String(assertNotNull(body)).contains("\"autoSelectedOptionKey\":null"))
            }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            client
                .get()
                .uri("$PRODUCTS/999/effective-options")
                .exchange()
                .expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    private fun post(path: String): WebTestClient.RequestBodySpec = client.post().uri("$PRODUCTS/1$path")

    private fun put(path: String): WebTestClient.RequestBodySpec = client.put().uri("$PRODUCTS/1$path")

    private fun delete(path: String): WebTestClient.RequestHeadersSpec<*> = client.delete().uri("$PRODUCTS/1$path")

    private fun send(
        request: WebTestClient.RequestBodySpec,
        ifMatch: String?,
        json: String,
    ): WebTestClient.ResponseSpec =
        request
            .headers { headers -> ifMatch?.let { headers.set(HttpHeaders.IF_MATCH, it) } }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()

    private fun send(
        request: WebTestClient.RequestHeadersSpec<*>,
        ifMatch: String?,
    ): WebTestClient.ResponseSpec =
        request
            .headers { headers -> ifMatch?.let { headers.set(HttpHeaders.IF_MATCH, it) } }
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

    private companion object {
        const val PRODUCTS = "/api/v1/admin/products"
    }
}
