package com.dozycoffee.catalog.product.presentation.optiongroup

import com.dozycoffee.catalog.support.ApiTest
import com.dozycoffee.catalog.support.expectProblem
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals

// 옵션 그룹 API(docs/api/product.md 옵션 그룹). 옵션 목록 규칙 자체는 유스케이스 테스트가 덮는다.
@DisplayName("옵션 그룹 API (요구사항 1.9)")
class OptionGroupApiTest : ApiTest() {
    @Nested
    @DisplayName("생성")
    inner class Register {
        @Test
        fun `생성하면 201과 Location, ETag와 함께 옵션 그룹을 준다`() {
            post(
                """
                {
                  "name": "사이즈", "selectionType": "SINGLE", "required": true,
                  "options": [
                    {"optionKey": "REGULAR", "name": "레귤러", "price": 0},
                    {"optionKey": "LARGE", "name": "라지", "price": 500}
                  ]
                }
                """,
            ).expectStatus()
                .isCreated
                .expectHeader()
                .location("/api/v1/admin/option-groups/1")
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(1)
                .jsonPath("$.name")
                .isEqualTo("사이즈")
                .jsonPath("$.selectionType")
                .isEqualTo("SINGLE")
                .jsonPath("$.required")
                .isEqualTo(true)
                .jsonPath("$.options[0].optionKey")
                .isEqualTo("REGULAR")
                .jsonPath("$.options[1].name")
                .isEqualTo("라지")
                .jsonPath("$.options[1].price")
                .isEqualTo(500)
                .jsonPath("$.version")
                .isEqualTo(0)
        }

        @Test
        fun `음수 가격은 400 INVALID_MONEY_AMOUNT`() {
            post(
                """
                {
                  "name": "사이즈", "selectionType": "SINGLE", "required": true,
                  "options": [{"optionKey": "REGULAR", "name": "레귤러", "price": -1}]
                }
                """,
            ).expectProblem(400, "INVALID_MONEY_AMOUNT")
            assertEquals(0, countBlocking("SELECT count(*) FROM option_groups"))
        }

        @Test
        fun `옵션이 없으면 422 EMPTY_OPTION_GROUP`() {
            post("""{"name": "사이즈", "selectionType": "SINGLE", "required": true, "options": []}""")
                .expectProblem(422, "EMPTY_OPTION_GROUP")
        }

        @Test
        fun `필수 여부를 빠뜨리면 400 INVALID_REQUEST`() {
            post("""{"name": "사이즈", "selectionType": "SINGLE", "options": []}""")
                .expectProblem(400, "INVALID_REQUEST")
        }

        private fun post(json: String): WebTestClient.ResponseSpec =
            client
                .post()
                .uri("/api/v1/admin/option-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json.trimIndent())
                .exchange()
    }

    @Nested
    @DisplayName("목록·조회")
    inner class Read {
        @Test
        fun `목록은 등록 순이고 ids와 keyword로 거른다`() {
            givenOptionGroup("사이즈", "REGULAR")
            givenOptionGroup("샷 추가", "SHOT")
            givenOptionGroup("시럽", "VANILLA")

            client
                .get()
                .uri("/api/v1/admin/option-groups")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[*].name")
                .isEqualTo(listOf("사이즈", "샷 추가", "시럽"))
                .jsonPath("$[0].options[0].optionKey")
                .isEqualTo("REGULAR")
                .jsonPath("$[0].version")
                .isEqualTo(0)

            client
                .get()
                .uri("/api/v1/admin/option-groups?ids=1,3&keyword=시")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(listOf(3))
        }

        @Test
        fun `조회하면 ETag에 버전을 담는다`() {
            givenOptionGroup("사이즈", "REGULAR", "LARGE")

            client
                .get()
                .uri("/api/v1/admin/option-groups/1")
                .exchange()
                .expectStatus()
                .isOk
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("사이즈")
                .jsonPath("$.options[*].optionKey")
                .isEqualTo(listOf("REGULAR", "LARGE"))
        }

        @Test
        fun `없는 옵션 그룹은 404 OPTION_GROUP_NOT_FOUND`() {
            client
                .get()
                .uri("/api/v1/admin/option-groups/999")
                .exchange()
                .expectProblem(404, "OPTION_GROUP_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("정의 변경")
    inner class ChangeDefinition {
        @Test
        fun `If-Match의 버전으로 바꾸고 새 버전을 ETag로 준다`() {
            givenOptionGroup("사이즈", "REGULAR")

            put("\"0\"")
                .expectStatus()
                .isOk
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"1\"")
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("컵 사이즈")
                .jsonPath("$.selectionType")
                .isEqualTo("MULTI")
                .jsonPath("$.required")
                .isEqualTo(false)
                .jsonPath("$.options[0].optionKey")
                .isEqualTo("REGULAR")
                .jsonPath("$.version")
                .isEqualTo(1)
        }

        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED`() {
            givenOptionGroup("사이즈", "REGULAR")

            put(null).expectProblem(428, "VERSION_REQUIRED")
        }

        @Test
        fun `오래된 버전이면 409 VERSION_CONFLICT와 현재 버전을 준다`() {
            givenOptionGroup("사이즈", "REGULAR")
            givenSql("UPDATE option_groups SET version = 2 WHERE id = 1")

            put("\"1\"")
                .expectProblem(409, "VERSION_CONFLICT")
                .jsonPath("$.currentVersion")
                .isEqualTo(2)
            assertEquals(1, countBlocking("SELECT count(*) FROM option_groups WHERE name = '사이즈'"))
        }

        private fun put(ifMatch: String?): WebTestClient.ResponseSpec =
            client
                .put()
                .uri("/api/v1/admin/option-groups/1")
                .headers { headers -> ifMatch?.let { headers.set(HttpHeaders.IF_MATCH, it) } }
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name": "컵 사이즈", "selectionType": "MULTI", "required": false}""")
                .exchange()
    }

    @Nested
    @DisplayName("옵션 목록 교체")
    inner class ReplaceOptions {
        @Test
        fun `입력한 목록 전체로 교체하고 새 버전을 준다`() {
            givenOptionGroup("사이즈", "REGULAR", "LARGE")

            put(
                "\"0\"",
                """{"options": [{"optionKey": "LARGE", "name": "라지", "price": 700}, {"optionKey": "VENTI", "name": "벤티", "price": 1000}]}""",
            ).expectStatus()
                .isOk
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"1\"")
                .expectBody()
                .jsonPath("$.options[*].optionKey")
                .isEqualTo(listOf("LARGE", "VENTI"))
                .jsonPath("$.options[0].price")
                .isEqualTo(700)
                .jsonPath("$.version")
                .isEqualTo(1)
        }

        @Test
        fun `음수 가격은 400 INVALID_MONEY_AMOUNT`() {
            givenOptionGroup("사이즈", "REGULAR")

            put("\"0\"", """{"options": [{"optionKey": "REGULAR", "name": "레귤러", "price": -500}]}""")
                .expectProblem(400, "INVALID_MONEY_AMOUNT")
        }

        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED`() {
            givenOptionGroup("사이즈", "REGULAR")

            put(null, """{"options": [{"optionKey": "REGULAR", "name": "레귤러", "price": 0}]}""")
                .expectProblem(428, "VERSION_REQUIRED")
        }

        @Test
        fun `오래된 버전이면 409 VERSION_CONFLICT와 현재 버전을 준다`() {
            givenOptionGroup("사이즈", "REGULAR")
            givenSql("UPDATE option_groups SET version = 3 WHERE id = 1")

            put("\"2\"", """{"options": [{"optionKey": "LARGE", "name": "라지", "price": 0}]}""")
                .expectProblem(409, "VERSION_CONFLICT")
                .jsonPath("$.currentVersion")
                .isEqualTo(3)
            assertEquals(1, countBlocking("SELECT count(*) FROM options WHERE option_key = 'REGULAR'"))
        }

        private fun put(
            ifMatch: String?,
            json: String,
        ): WebTestClient.ResponseSpec =
            client
                .put()
                .uri("/api/v1/admin/option-groups/1/options")
                .headers { headers -> ifMatch?.let { headers.set(HttpHeaders.IF_MATCH, it) } }
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
    }

    @Nested
    @DisplayName("삭제")
    inner class Delete {
        @Test
        fun `삭제하면 204`() {
            givenOptionGroup("사이즈", "REGULAR")

            client
                .delete()
                .uri("/api/v1/admin/option-groups/1")
                .exchange()
                .expectStatus()
                .isNoContent
                .expectBody()
                .isEmpty
            assertEquals(0, countBlocking("SELECT count(*) FROM option_groups"))
        }

        @Test
        fun `연결한 상품이 있으면 409 OPTION_GROUP_STILL_REFERENCED`() {
            givenOptionGroup("사이즈", "REGULAR")
            givenSql("INSERT INTO categories (name) VALUES ('음료')")
            givenSql("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            givenSql(
                "INSERT INTO products (name, category_id, base_price, tracks_inventory, status) VALUES ('아메리카노', 2, 4500, false, 'DRAFT')",
            )
            givenSql("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 1, 0)")

            client
                .delete()
                .uri("/api/v1/admin/option-groups/1")
                .exchange()
                .expectProblem(409, "OPTION_GROUP_STILL_REFERENCED")
            assertEquals(1, countBlocking("SELECT count(*) FROM option_groups"))
        }
    }

    private fun givenOptionGroup(
        name: String,
        vararg optionKeys: String,
    ) = runBlocking {
        execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('$name', 'SINGLE', true)")
        val id = count("SELECT max(id) FROM option_groups")
        optionKeys.forEachIndexed { index, key ->
            execute(
                "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                    "VALUES ($id, '$key', '$key', 0, $index)",
            )
        }
    }

    private fun givenSql(sql: String) = runBlocking { execute(sql) }

    private fun countBlocking(sql: String): Long = runBlocking { count(sql) }
}
