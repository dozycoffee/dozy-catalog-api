package com.dozycoffee.catalog.product.presentation.category

import com.dozycoffee.catalog.product.presentation.expectProblem
import com.dozycoffee.catalog.support.ApiTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 카테고리 API(docs/api/product.md 카테고리). 계층 규칙 자체는 유스케이스 테스트가 덮는다.
// 준비 데이터: 1 음료(대분류) ─ 2 커피(소분류), 3 MD(대분류) ─ 4 텀블러(소분류), 5 디저트(대분류, 소분류 없음)
@DisplayName("카테고리 API (요구사항 1.6)")
class CategoryApiTest : ApiTest() {
    @BeforeEach
    fun givenCategories() =
        runBlocking {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO categories (name) VALUES ('MD')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('텀블러', 3)")
            execute("INSERT INTO categories (name) VALUES ('디저트')")
        }

    @Nested
    @DisplayName("목록")
    inner class Listing {
        @Test
        fun `대분류와 소분류를 등록 순의 평평한 목록으로 준다`() {
            val body =
                get("/api/v1/admin/categories")
                    .expectBody()
                    .jsonPath("$[*].id")
                    .isEqualTo(listOf(1, 2, 3, 4, 5))
                    .jsonPath("$[1].name")
                    .isEqualTo("커피")
                    .jsonPath("$[1].parentId")
                    .isEqualTo(1)
                    .returnResult()
                    .responseBody
            // jsonPath는 null 값과 없는 필드를 구분하지 못해 본문에서 직접 확인한다.
            assertTrue(String(assertNotNull(body)).contains("""{"id":1,"name":"음료","parentId":null}"""), String(body))
        }

        @Test
        fun `parentId를 주면 그 대분류의 소분류만 준다`() {
            get("/api/v1/admin/categories?parentId=3")
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(listOf(4))
        }

        @Test
        fun `topLevel이 true면 대분류만 준다`() {
            get("/api/v1/admin/categories?topLevel=true")
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(listOf(1, 3, 5))
        }

        @Test
        fun `ids와 다른 조건을 함께 주면 모두 만족하는 것만 준다`() {
            get("/api/v1/admin/categories?ids=1,2,4&topLevel=true")
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(listOf(1))
        }

        private fun get(uri: String): WebTestClient.ResponseSpec =
            client
                .get()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isOk
    }

    @Nested
    @DisplayName("등록")
    inner class Register {
        @Test
        fun `parentId가 null이면 대분류로 등록한다`() {
            val body =
                post("""{"name": "푸드", "parentId": null}""")
                    .expectStatus()
                    .isCreated
                    .expectHeader()
                    .location("/api/v1/admin/categories/6")
                    .expectBody()
                    .jsonPath("$.id")
                    .isEqualTo(6)
                    .jsonPath("$.name")
                    .isEqualTo("푸드")
                    .returnResult()
                    .responseBody
            assertTrue(String(assertNotNull(body)).contains("\"parentId\":null"), String(body))
        }

        @Test
        fun `parentId가 있으면 그 대분류의 소분류로 등록한다`() {
            post("""{"name": "에이드", "parentId": 1}""")
                .expectStatus()
                .isCreated
                .expectHeader()
                .location("/api/v1/admin/categories/6")
                .expectBody()
                .jsonPath("$.parentId")
                .isEqualTo(1)
        }

        @Test
        fun `소분류를 부모로 지정하면 404 TOP_LEVEL_CATEGORY_NOT_FOUND`() {
            post("""{"name": "에스프레소", "parentId": 2}""")
                .expectProblem(404, "TOP_LEVEL_CATEGORY_NOT_FOUND")
        }

        private fun post(json: String): WebTestClient.ResponseSpec =
            client
                .post()
                .uri("/api/v1/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
    }

    @Nested
    @DisplayName("이름 변경")
    inner class Rename {
        @Test
        fun `이름을 바꾸고 카테고리를 준다`() {
            put("/api/v1/admin/categories/2", """{"name": "에스프레소 음료"}""")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(2)
                .jsonPath("$.name")
                .isEqualTo("에스프레소 음료")
                .jsonPath("$.parentId")
                .isEqualTo(1)
        }

        @Test
        fun `없는 카테고리는 404 CATEGORY_NOT_FOUND`() {
            put("/api/v1/admin/categories/999", """{"name": "커피"}""")
                .expectProblem(404, "CATEGORY_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("부모 변경")
    inner class ChangeParent {
        @Test
        fun `소분류에 다른 대분류를 주면 그 대분류로 이동한다`() {
            changeParent(2, """{"parentId": 3}""")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(2)
                .jsonPath("$.parentId")
                .isEqualTo(3)
        }

        @Test
        fun `소분류가 없는 대분류에 다른 대분류를 주면 소분류로 강등한다`() {
            changeParent(5, """{"parentId": 1}""")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(5)
                .jsonPath("$.parentId")
                .isEqualTo(1)
        }

        @Test
        fun `소분류에 null을 주면 대분류로 승격한다`() {
            val body =
                changeParent(4, """{"parentId": null}""")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.id")
                    .isEqualTo(4)
                    .returnResult()
                    .responseBody
            assertTrue(String(assertNotNull(body)).contains("\"parentId\":null"), String(body))
            assertEquals(1, countBlocking("SELECT count(*) FROM categories WHERE id = 4 AND parent_category_id IS NULL"))
        }

        @Test
        fun `소분류를 가진 대분류를 강등하면 409 CATEGORY_WITH_CHILDREN_NOT_DEMOTABLE`() {
            changeParent(1, """{"parentId": 3}""")
                .expectProblem(409, "CATEGORY_WITH_CHILDREN_NOT_DEMOTABLE")
        }

        @Test
        fun `parentId 필드를 빠뜨리면 승격으로 읽지 않고 400 INVALID_REQUEST`() {
            changeParent(4, "{}")
                .expectProblem(400, "INVALID_REQUEST")
            assertEquals(1, countBlocking("SELECT count(*) FROM categories WHERE id = 4 AND parent_category_id = 3"))
        }

        private fun changeParent(
            categoryId: Long,
            json: String,
        ): WebTestClient.ResponseSpec = put("/api/v1/admin/categories/$categoryId/parent", json)
    }

    @Nested
    @DisplayName("삭제")
    inner class Delete {
        @Test
        fun `삭제하면 204`() {
            delete(5)
                .expectStatus()
                .isNoContent
                .expectBody()
                .isEmpty
            assertEquals(0, countBlocking("SELECT count(*) FROM categories WHERE id = 5"))
        }

        @Test
        fun `상품이 참조 중인 소분류는 409 CATEGORY_STILL_REFERENCED`() {
            runBlocking {
                execute(
                    "INSERT INTO products (name, category_id, base_price, tracks_inventory, status) VALUES ('아메리카노', 2, 4500, false, 'DRAFT')",
                )
            }

            delete(2).expectProblem(409, "CATEGORY_STILL_REFERENCED")
            assertEquals(1, countBlocking("SELECT count(*) FROM categories WHERE id = 2"))
        }

        @Test
        fun `소분류를 가진 대분류는 409 CATEGORY_HAS_CHILDREN`() {
            delete(1).expectProblem(409, "CATEGORY_HAS_CHILDREN")
        }

        private fun delete(categoryId: Long): WebTestClient.ResponseSpec =
            client
                .delete()
                .uri("/api/v1/admin/categories/$categoryId")
                .exchange()
    }

    private fun put(
        uri: String,
        json: String,
    ): WebTestClient.ResponseSpec =
        client
            .put()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()

    private fun countBlocking(sql: String): Long = runBlocking { count(sql) }
}
