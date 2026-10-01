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

// 상품 API(docs/api/product.md 상품): 등록·목록·조회·교체·삭제, 상태 전환, 판매 범위.
// 규칙 자체는 유스케이스 테스트가 덮으므로 응답 형식, 상태 코드 매핑, If-Match, 목록 파라미터만 확인한다.
// 검증 대상이 아닌 카테고리·태그·상품 그룹·옵션 그룹과 기존 상품은 SQL로 넣는다(docs/architecture/testing.md).
@DisplayName("상품 API")
class ProductApiTest : ApiTest() {
    @BeforeEach
    fun prepareReferences() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1), ('차', 1)")
            execute("INSERT INTO categories (name) VALUES ('MD')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('텀블러', 4)")
            execute("INSERT INTO tags (name) VALUES ('신메뉴'), ('베스트')")
            execute("INSERT INTO product_groups (name) VALUES ('시즌 운영'), ('본사 추천')")
            execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('사이즈', 'SINGLE', true), ('샷', 'MULTI', false)")
            execute(
                """
                INSERT INTO options (option_group_id, option_key, name, price, display_order)
                VALUES (1, 'REGULAR', '레귤러', 0, 0), (1, 'LARGE', '라지', 500, 1), (2, 'SHOT', '샷 추가', 500, 0)
                """.trimIndent(),
            )
        }

    @Nested
    @DisplayName("POST /products — 등록")
    inner class Register {
        @Test
        fun `201과 Location, ETag를 주고 등록한 상품을 돌려준다`() =
            runTest {
                var tagIds: List<Int> = emptyList()
                val body =
                    post(
                        """
                        {
                          "name": "아이스 아메리카노", "categoryId": 2, "basePrice": 4500, "tracksInventory": false,
                          "description": null, "imageUrl": "https://cdn.example.com/p/1.png",
                          "tagNames": ["베스트", "새 태그"], "groupIds": [2, 1], "optionGroupIds": [2, 1]
                        }
                        """.trimIndent(),
                    ).expectStatus()
                        .isCreated
                        .expectHeader()
                        .valueEquals(HttpHeaders.LOCATION, "/api/v1/admin/products/1")
                        .expectHeader()
                        .valueEquals(HttpHeaders.ETAG, "\"0\"")
                        .expectBody()
                        .jsonPath("$.id")
                        .isEqualTo(1)
                        .jsonPath("$.sku")
                        .value<String> { sku -> assertTrue(Regex("DZ-\\d{8}").matches(sku), sku) }
                        .jsonPath("$.name")
                        .isEqualTo("아이스 아메리카노")
                        .jsonPath("$.status")
                        .isEqualTo("DRAFT")
                        .jsonPath("$.categoryId")
                        .isEqualTo(2)
                        .jsonPath("$.imageUrl")
                        .isEqualTo("https://cdn.example.com/p/1.png")
                        .jsonPath("$.basePrice")
                        .isEqualTo(4500)
                        .jsonPath("$.tracksInventory")
                        .isEqualTo(false)
                        .jsonPath("$.tagIds")
                        .value<List<Int>> { tagIds = it }
                        .jsonPath("$.groupIds")
                        .isEqualTo(listOf(1, 2))
                        .jsonPath("$.storeScope.kind")
                        .isEqualTo("ALL")
                        .jsonPath("$.storeScope.targetStoreIds")
                        .isEmpty
                        .jsonPath("$.optionGroups[*].optionGroupId")
                        .isEqualTo(listOf(2, 1))
                        .jsonPath("$.optionGroups[0].overrides")
                        .isEmpty
                        .jsonPath("$.version")
                        .isEqualTo(0)
                        .returnResult()
                        .responseBody
                // jsonPath는 null 값과 없는 필드를 구분하지 못해 본문에서 직접 확인한다.
                assertTrue(String(assertNotNull(body)).contains("\"description\":null"))
                // 기존 태그(베스트)는 재사용하고 없는 태그는 새로 만든다. 새 태그의 ID는 시퀀스가 정하므로 DB에서 읽는다.
                val newTagId = count("SELECT id FROM tags WHERE name = '새 태그'").toInt()
                assertEquals(listOf(2, newTagId).sorted(), tagIds)
            }

        @Test
        fun `선택 필드를 생략하면 빈 값으로 등록한다`() {
            post("""{"name": "아메리카노", "categoryId": 2, "basePrice": 4000, "tracksInventory": true}""")
                .expectStatus()
                .isCreated
                .expectBody()
                .jsonPath("$.tracksInventory")
                .isEqualTo(true)
                .jsonPath("$.tagIds")
                .isEmpty
                .jsonPath("$.groupIds")
                .isEmpty
                .jsonPath("$.optionGroups")
                .isEmpty
        }

        @Test
        fun `기준가가 음수면 400 INVALID_MONEY_AMOUNT`() {
            post("""{"name": "아메리카노", "categoryId": 2, "basePrice": -1, "tracksInventory": false}""")
                .expectProblem(400, "INVALID_MONEY_AMOUNT")
        }

        @Test
        fun `기준가가 없으면 0으로 읽지 않고 400 INVALID_REQUEST`() {
            post("""{"name": "아메리카노", "categoryId": 2, "tracksInventory": false}""")
                .expectProblem(400, "INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 본문의 'basePrice' 값이 없거나 형식이 맞지 않습니다")
        }

        @Test
        fun `재고 추적 여부가 없으면 false로 읽지 않고 400 INVALID_REQUEST`() {
            post("""{"name": "아메리카노", "categoryId": 2, "basePrice": 4000}""")
                .expectProblem(400, "INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 본문의 'tracksInventory' 값이 없거나 형식이 맞지 않습니다")
        }

        @Test
        fun `대분류를 지정하면 400 CATEGORY_NOT_ASSIGNABLE`() {
            post("""{"name": "아메리카노", "categoryId": 1, "basePrice": 4000, "tracksInventory": false}""")
                .expectProblem(400, "CATEGORY_NOT_ASSIGNABLE")
        }

        @Test
        fun `같은 옵션 그룹을 두 번 지정하면 409 DUPLICATE_OPTION_GROUP_LINK`() {
            post("""{"name": "아메리카노", "categoryId": 2, "basePrice": 4000, "tracksInventory": false, "optionGroupIds": [1, 1]}""")
                .expectProblem(409, "DUPLICATE_OPTION_GROUP_LINK")
        }

        private fun post(json: String): WebTestClient.ResponseSpec =
            client
                .post()
                .uri(PRODUCTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
    }

    @Nested
    @DisplayName("GET /products/{productId} — 조회")
    inner class Get {
        @Test
        fun `상품과 ETag를 돌려준다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE", version = 3)
                execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, 1)")
                execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (1, 2)")
                execute("UPDATE products SET store_scope = 'LIMITED' WHERE id = 1")
                execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (1, 102), (1, 101)")
                execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 1, 0)")
                execute(
                    """
                    INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price)
                    VALUES (1, 1, 'LARGE', 'PRICE', 700), (1, 1, 'REGULAR', 'EXCLUDE', NULL)
                    """.trimIndent(),
                )

                val body =
                    get(1)
                        .expectStatus()
                        .isOk
                        .expectHeader()
                        .valueEquals(HttpHeaders.ETAG, "\"3\"")
                        .expectBody()
                        .jsonPath("$.sku")
                        .isEqualTo("DZ-00000001")
                        .jsonPath("$.status")
                        .isEqualTo("ACTIVE")
                        .jsonPath("$.tagIds")
                        .isEqualTo(listOf(1))
                        .jsonPath("$.groupIds")
                        .isEqualTo(listOf(2))
                        .jsonPath("$.storeScope.kind")
                        .isEqualTo("LIMITED")
                        .jsonPath("$.storeScope.targetStoreIds")
                        .isEqualTo(listOf(101, 102))
                        .jsonPath("$.optionGroups[0].optionGroupId")
                        .isEqualTo(1)
                        .jsonPath("$.optionGroups[0].overrides[0].optionKey")
                        .isEqualTo("LARGE")
                        .jsonPath("$.optionGroups[0].overrides[0].type")
                        .isEqualTo("PRICE")
                        .jsonPath("$.optionGroups[0].overrides[0].price")
                        .isEqualTo(700)
                        .jsonPath("$.optionGroups[0].overrides[1].optionKey")
                        .isEqualTo("REGULAR")
                        .jsonPath("$.optionGroups[0].overrides[1].type")
                        .isEqualTo("EXCLUDE")
                        .jsonPath("$.version")
                        .isEqualTo(3)
                        .returnResult()
                        .responseBody
                // EXCLUDE 예외의 price는 null로 나간다. jsonPath는 null 값과 없는 필드를 구분하지 못해 본문에서 확인한다.
                assertTrue(String(assertNotNull(body)).contains("\"price\":null"))
            }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            get(999).expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("PUT /products/{productId} — 전체 교체")
    inner class Replace {
        private val body = """{"name": "아이스 아메리카노", "categoryId": 3, "basePrice": 4800, "tagNames": ["베스트"], "groupIds": [1]}"""

        @Test
        fun `If-Match의 버전이 맞으면 교체하고 새 버전을 돌려준다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "DISCONTINUED", version = 2)

                put(1, ifMatch = "\"2\"", json = body)
                    .expectStatus()
                    .isOk
                    .expectHeader()
                    .valueEquals(HttpHeaders.ETAG, "\"3\"")
                    .expectBody()
                    .jsonPath("$.name")
                    .isEqualTo("아이스 아메리카노")
                    .jsonPath("$.categoryId")
                    .isEqualTo(3)
                    .jsonPath("$.basePrice")
                    .isEqualTo(4800)
                    .jsonPath("$.status")
                    .isEqualTo("DISCONTINUED")
                    .jsonPath("$.tagIds")
                    .isEqualTo(listOf(2))
                    .jsonPath("$.groupIds")
                    .isEqualTo(listOf(1))
                    .jsonPath("$.version")
                    .isEqualTo(3)
            }

        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED이고 바꾸지 않는다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "DRAFT", version = 2)

                put(1, ifMatch = null, json = body).expectProblem(428, "VERSION_REQUIRED")

                assertEquals(1, count("SELECT count(*) FROM products WHERE name = '아메리카노' AND version = 2"))
            }

        @Test
        fun `그 사이 다른 변경이 반영됐으면 409 VERSION_CONFLICT와 현재 버전을 준다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "DRAFT", version = 5)

                put(1, ifMatch = "\"4\"", json = body)
                    .expectProblem(409, "VERSION_CONFLICT")
                    .jsonPath("$.currentVersion")
                    .isEqualTo(5)

                assertEquals(1, count("SELECT count(*) FROM products WHERE name = '아메리카노' AND version = 5"))
            }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            put(999, ifMatch = "\"0\"", json = body).expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("DELETE /products/{productId} — 삭제")
    inner class Delete {
        @Test
        fun `Draft 상품을 지우고 204를 준다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "DRAFT")

                delete(1)
                    .expectStatus()
                    .isNoContent
                    .expectBody()
                    .isEmpty

                assertEquals(0, count("SELECT count(*) FROM products"))
            }

        @Test
        fun `Draft가 아니면 409 PRODUCT_NOT_DELETABLE`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")

                delete(1).expectProblem(409, "PRODUCT_NOT_DELETABLE")

                assertEquals(1, count("SELECT count(*) FROM products"))
            }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            delete(999).expectProblem(404, "PRODUCT_NOT_FOUND")
        }

        private fun delete(productId: Long): WebTestClient.ResponseSpec = client.delete().uri("$PRODUCTS/$productId").exchange()
    }

    @Nested
    @DisplayName("POST /products/{productId}/activate, /discontinue — 상태 전환")
    inner class StatusTransition {
        @Test
        fun `활성화하면 ACTIVE 상품과 새 ETag를 돌려준다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "DRAFT", version = 1)

                transition(1, "activate")
                    .expectStatus()
                    .isOk
                    .expectHeader()
                    .valueEquals(HttpHeaders.ETAG, "\"2\"")
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo("ACTIVE")
                    .jsonPath("$.version")
                    .isEqualTo(2)
            }

        @Test
        fun `단종하면 DISCONTINUED 상품을 돌려준다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")

                transition(1, "discontinue")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo("DISCONTINUED")
            }

        @Test
        fun `이미 ACTIVE인 상품을 활성화하면 409 INVALID_PRODUCT_STATUS_TRANSITION`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")

                transition(1, "activate").expectProblem(409, "INVALID_PRODUCT_STATUS_TRANSITION")
            }

        @Test
        fun `Draft 상품을 단종하면 409 INVALID_PRODUCT_STATUS_TRANSITION`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "DRAFT")

                transition(1, "discontinue").expectProblem(409, "INVALID_PRODUCT_STATUS_TRANSITION")
            }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            transition(999, "activate").expectProblem(404, "PRODUCT_NOT_FOUND")
        }

        private fun transition(
            productId: Long,
            action: String,
        ): WebTestClient.ResponseSpec = client.post().uri("$PRODUCTS/$productId/$action").exchange()
    }

    @Nested
    @DisplayName("PUT /products/{productId}/store-scope — 판매 범위")
    inner class StoreScope {
        @Test
        fun `LIMITED로 바꾸면 대상 매장을 담아 돌려준다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")

                putScope(1, "\"0\"", """{"kind": "LIMITED", "targetStoreIds": [102, 101]}""")
                    .expectStatus()
                    .isOk
                    .expectHeader()
                    .valueEquals(HttpHeaders.ETAG, "\"1\"")
                    .expectBody()
                    .jsonPath("$.storeScope.kind")
                    .isEqualTo("LIMITED")
                    .jsonPath("$.storeScope.targetStoreIds")
                    .isEqualTo(listOf(101, 102))
            }

        @Test
        fun `대상 매장을 비운 LIMITED도 받는다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")

                putScope(1, "\"0\"", """{"kind": "LIMITED", "targetStoreIds": []}""")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.storeScope.kind")
                    .isEqualTo("LIMITED")
                    .jsonPath("$.storeScope.targetStoreIds")
                    .isEmpty
            }

        @Test
        fun `ALL로 바꾸면 대상 매장은 빈 배열이다`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")
                execute("UPDATE products SET store_scope = 'LIMITED' WHERE id = 1")
                execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (1, 101)")

                putScope(1, "\"0\"", """{"kind": "ALL"}""")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.storeScope.kind")
                    .isEqualTo("ALL")
                    .jsonPath("$.storeScope.targetStoreIds")
                    .isEmpty
            }

        @Test
        fun `ALL에 대상 매장을 담으면 400 INVALID_REQUEST`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")

                putScope(1, "\"0\"", """{"kind": "ALL", "targetStoreIds": [101]}""").expectProblem(400, "INVALID_REQUEST")
            }

        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED`() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")

                putScope(1, null, """{"kind": "ALL"}""").expectProblem(428, "VERSION_REQUIRED")
            }

        private fun putScope(
            productId: Long,
            ifMatch: String?,
            json: String,
        ): WebTestClient.ResponseSpec =
            client
                .put()
                .uri("$PRODUCTS/$productId/store-scope")
                .headers { headers -> ifMatch?.let { headers.set(HttpHeaders.IF_MATCH, it) } }
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
    }

    @Nested
    @DisplayName("GET /products — 목록·검색")
    inner class Search {
        // 상품 ID는 넣은 순서대로 1부터 매겨진다. 목록은 최근 등록한 상품(큰 ID)이 먼저 온다.
        @BeforeEach
        fun prepareProducts() =
            runTest {
                insertProduct("아메리카노", categoryId = 2, status = "ACTIVE")
                insertProduct("카페라떼", categoryId = 2, status = "DRAFT")
                insertProduct("얼그레이", categoryId = 3, status = "ACTIVE")
                insertProduct("텀블러", categoryId = 5, status = "DISCONTINUED")
                insertProduct("아이스 아메리카노", categoryId = 2, status = "ACTIVE")
                execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, 1), (5, 1), (3, 2)")
                execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (5, 1), (2, 1)")
            }

        @Test
        fun `조건 없이 부르면 최근 등록 순으로 첫 페이지를 준다`() {
            search("")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content[*].id")
                .isEqualTo(listOf(5, 4, 3, 2, 1))
                .jsonPath("$.content[0].name")
                .isEqualTo("아이스 아메리카노")
                .jsonPath("$.content[0].groupIds")
                .isEqualTo(listOf(1))
                .jsonPath("$.page")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(20)
                .jsonPath("$.totalElements")
                .isEqualTo(5)
                .jsonPath("$.totalPages")
                .isEqualTo(1)
        }

        @Test
        fun `페이지 크기와 번호로 나눠 준다`() {
            search("page=1&size=2")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content[*].id")
                .isEqualTo(listOf(3, 2))
                .jsonPath("$.page")
                .isEqualTo(1)
                .jsonPath("$.size")
                .isEqualTo(2)
                .jsonPath("$.totalElements")
                .isEqualTo(5)
                .jsonPath("$.totalPages")
                .isEqualTo(3)
        }

        @Test
        fun `검색어는 상품명 일부나 SKU 전체로 찾는다`() {
            expectIds("keyword=아메리카노", 5, 1)
            expectIds("keyword=DZ-00000003", 3)
        }

        @Test
        fun `대분류를 주면 그 아래 소분류의 상품을 모두 찾는다`() {
            expectIds("categoryId=1", 5, 3, 2, 1)
            expectIds("categoryId=3", 3)
        }

        @Test
        fun `태그, 상품 그룹, 상태로 거른다`() {
            expectIds("tagId=1", 5, 1)
            expectIds("groupId=1", 5, 2)
            expectIds("status=ACTIVE", 5, 3, 1)
        }

        @Test
        fun `ids로 고른 상품만 주고 다른 조건과 함께 주면 모두 만족하는 것만 남긴다`() {
            expectIds("ids=1,4,999", 4, 1)
            expectIds("ids=1,4&status=ACTIVE", 1)
        }

        @Test
        fun `허용되지 않는 상태 값이면 400 INVALID_REQUEST`() {
            search("status=SOLD").expectProblem(400, "INVALID_REQUEST")
        }

        private fun expectIds(
            query: String,
            vararg ids: Int,
        ) {
            search(query)
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content[*].id")
                .isEqualTo(ids.toList())
                .jsonPath("$.totalElements")
                .isEqualTo(ids.size)
        }

        private fun search(query: String): WebTestClient.ResponseSpec = client.get().uri("$PRODUCTS?$query").exchange()
    }

    private fun get(productId: Long): WebTestClient.ResponseSpec = client.get().uri("$PRODUCTS/$productId").exchange()

    private fun put(
        productId: Long,
        ifMatch: String?,
        json: String,
    ): WebTestClient.ResponseSpec =
        client
            .put()
            .uri("$PRODUCTS/$productId")
            .headers { headers -> ifMatch?.let { headers.set(HttpHeaders.IF_MATCH, it) } }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()

    // SKU는 넣은 순서대로 DZ-00000001부터 매긴다(시퀀스를 거치지 않는 준비 데이터).
    private var insertedProducts = 0

    private suspend fun insertProduct(
        name: String,
        categoryId: Long,
        status: String,
        version: Long = 0,
    ) {
        insertedProducts++
        execute(
            """
            INSERT INTO products (sku, name, category_id, base_price, status, store_scope, tracks_inventory, version)
            VALUES ('DZ-${"%08d".format(insertedProducts)}', '$name', $categoryId, 4500, '$status', 'ALL', false, $version)
            """.trimIndent(),
        )
    }

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
