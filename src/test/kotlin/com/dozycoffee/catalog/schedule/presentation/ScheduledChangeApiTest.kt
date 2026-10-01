package com.dozycoffee.catalog.schedule.presentation

import com.dozycoffee.catalog.support.ApiTest
import com.dozycoffee.catalog.support.FakeStoreExistenceValidator
import com.dozycoffee.catalog.support.MutableClock
import com.dozycoffee.catalog.support.MutableClockConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.json.JsonAssert
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals

// 예약 API(docs/api/schedule.md). 등록 규칙 자체는 ScheduledChangeApplicationServiceTest가 덮으므로
// 여기서는 경로의 필드와 값 형식의 변환, 오류 응답의 대표 사례를 본다(docs/architecture/testing.md API 테스트).
// 오늘은 MutableClock 기준 2026-09-21(업무 시간대 Asia/Seoul)이다.
@Import(MutableClockConfiguration::class)
@DisplayName("예약 API (요구사항 1.4)")
class ScheduledChangeApiTest : ApiTest() {
    @Autowired
    private lateinit var clock: MutableClock

    @Autowired
    private lateinit var storeValidator: FakeStoreExistenceValidator

    private var beverage = 0L
    private var coffee = 0L
    private var product = 0L
    private var sizeGroup = 0L
    private var shotGroup = 0L
    private var seasonGroup = 0L

    // 검증 대상이 아닌 준비 데이터는 유스케이스를 거치지 않고 직접 넣는다(docs/architecture/testing.md).
    @BeforeEach
    fun setUp() =
        runTest {
            clock.reset()
            storeValidator.reset()
            execute("INSERT INTO categories (name) VALUES ('음료')")
            beverage = count("SELECT max(id) FROM categories")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', $beverage)")
            coffee = count("SELECT max(id) FROM categories")
            execute(
                "INSERT INTO products (name, category_id, base_price, tracks_inventory, status) " +
                    "VALUES ('아메리카노', $coffee, 4500, false, 'DRAFT')",
            )
            product = count("SELECT max(id) FROM products")
            sizeGroup = insertOptionGroup("사이즈", "TALL", "GRANDE")
            shotGroup = insertOptionGroup("샷 추가", "EXTRA")
            execute("INSERT INTO product_groups (name) VALUES ('시즌')")
            seasonGroup = count("SELECT max(id) FROM product_groups")
        }

    @Nested
    @DisplayName("등록과 조회")
    inner class RegisterAndList {
        @ParameterizedTest(name = "{0} = {1}")
        @MethodSource("com.dozycoffee.catalog.schedule.presentation.ScheduledChangeApiTest#productFieldValues")
        fun `상품 필드의 예약을 등록하면 경로의 필드 이름과 요청 형식 그대로 돌려주고 목록에도 나온다`(
            field: String,
            requestValue: String,
            expectedValue: String,
        ) {
            val path = resolve(field)
            val expected = scheduledChangeJson(path, resolve(expectedValue), "2026-10-01")

            putProduct(path, """{"effectiveDate": "2026-10-01", "value": ${resolve(requestValue)}}""")
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected, JsonCompareMode.STRICT)

            listProduct()
                .expectStatus()
                .isOk
                .expectBody()
                .json("[$expected]", JsonCompareMode.STRICT)
        }

        @Test
        fun `옵션 목록 예약을 등록하면 옵션 목록 전체를 순서대로 돌려준다`() {
            val options = """[{"optionKey": "TALL", "name": "톨", "price": 0}, {"optionKey": "GRANDE", "name": "그란데", "price": 500}]"""
            val expected = scheduledChangeJson("options", options, "2026-10-01")

            client
                .put()
                .uri("/api/v1/admin/option-groups/$sizeGroup/scheduled-changes/options")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"effectiveDate": "2026-10-01", "value": $options}""")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected, JsonCompareMode.STRICT)

            listOptionGroup(sizeGroup)
                .expectStatus()
                .isOk
                .expectBody()
                .json("[$expected]", JsonCompareMode.STRICT)
        }

        @Test
        fun `값이 없는 필드는 value를 빼고 보내도 되고 응답에는 null로 담는다`() {
            val body =
                putProduct("activation", """{"effectiveDate": "2026-10-01"}""")
                    .expectStatus()
                    .isOk
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody
            // jsonPath는 null 값과 없는 필드를 구분하지 못해 본문에서 직접 확인한다.
            assertEquals(true, body?.contains("\"value\":null"), body)
        }

        @Test
        fun `태그는 이름으로 받아 없는 태그를 만들고 응답에는 태그 ID를 담는다`() =
            runTest {
                execute("INSERT INTO tags (name) VALUES ('베스트')")
                val best = count("SELECT id FROM tags WHERE name = '베스트'")

                val registered =
                    putProduct("tags", """{"effectiveDate": "2026-10-01", "value": ["신메뉴", "베스트"]}""")
                        .expectStatus()
                        .isOk
                        .expectBody(String::class.java)
                        .returnResult()
                        .responseBody

                val newMenu = count("SELECT id FROM tags WHERE name = '신메뉴'")
                assertEquals(2, count("SELECT count(*) FROM tags"))
                val expected = scheduledChangeJson("tags", listOf(best, newMenu).sorted().joinToString(",", "[", "]"), "2026-10-01")
                JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, registered)
                listProduct().expectBody().json("[$expected]", JsonCompareMode.STRICT)
            }

        @Test
        fun `활성화·단종과 옵션 그룹별 예외는 서로 다른 필드라 함께 대기한다`() {
            putProduct("activation", """{"effectiveDate": "2026-10-01", "value": null}""").expectStatus().isOk
            putProduct("discontinuation", """{"effectiveDate": "2026-10-31", "value": null}""").expectStatus().isOk
            putProduct(
                "option-overrides/$sizeGroup",
                """{"effectiveDate": "2026-10-01", "value": [{"optionKey": "GRANDE", "type": "PRICE", "price": 700}]}""",
            ).expectStatus().isOk
            putProduct(
                "option-overrides/$shotGroup",
                """{"effectiveDate": "2026-10-02", "value": [{"optionKey": "EXTRA", "type": "EXCLUDE"}]}""",
            ).expectStatus().isOk

            val sizeOverrides = """[{"optionKey": "GRANDE", "type": "PRICE", "price": 700}]"""
            val shotOverrides = """[{"optionKey": "EXTRA", "type": "EXCLUDE", "price": null}]"""
            val expected =
                listOf(
                    scheduledChangeJson("activation", "null", "2026-10-01"),
                    scheduledChangeJson("discontinuation", "null", "2026-10-31"),
                    scheduledChangeJson("option-overrides/$sizeGroup", sizeOverrides, "2026-10-01"),
                    scheduledChangeJson("option-overrides/$shotGroup", shotOverrides, "2026-10-02"),
                ).joinToString(",", "[", "]")

            listProduct()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected, JsonCompareMode.STRICT)
        }

        @Test
        fun `같은 필드에 다시 등록하면 기존 대기 예약을 대체한다`() =
            runTest {
                putProduct("basePrice", """{"effectiveDate": "2026-10-01", "value": 4800}""").expectStatus().isOk
                putProduct("basePrice", """{"effectiveDate": "2026-10-05", "value": 5000}""").expectStatus().isOk

                listProduct()
                    .expectBody()
                    .json("[${scheduledChangeJson("basePrice", "5000", "2026-10-05")}]", JsonCompareMode.STRICT)
                assertEquals(1, count("SELECT count(*) FROM scheduled_changes WHERE status = 'CANCELLED'"))
            }

        @Test
        fun `대기 예약이 없으면 빈 배열이다`() {
            listProduct()
                .expectStatus()
                .isOk
                .expectBody()
                .json("[]", JsonCompareMode.STRICT)
        }
    }

    @Nested
    @DisplayName("취소")
    inner class Cancel {
        @Test
        fun `대기 예약을 취소하면 204이고 목록에서 빠진다`() =
            runTest {
                putProduct("name", """{"effectiveDate": "2026-10-01", "value": "라떼"}""").expectStatus().isOk

                deleteProduct("name")
                    .expectStatus()
                    .isNoContent
                    .expectBody()
                    .isEmpty

                listProduct().expectBody().json("[]", JsonCompareMode.STRICT)
                assertEquals(1, count("SELECT count(*) FROM scheduled_changes WHERE status = 'CANCELLED'"))
            }

        @Test
        fun `옵션 그룹별 예외와 옵션 목록 예약도 경로의 필드 이름으로 취소한다`() {
            putProduct(
                "option-overrides/$sizeGroup",
                """{"effectiveDate": "2026-10-01", "value": [{"optionKey": "TALL", "type": "EXCLUDE"}]}""",
            ).expectStatus().isOk
            client
                .put()
                .uri("/api/v1/admin/option-groups/$sizeGroup/scheduled-changes/options")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"effectiveDate": "2026-10-01", "value": [{"optionKey": "TALL", "name": "톨", "price": 0}]}""")
                .exchange()
                .expectStatus()
                .isOk

            deleteProduct("option-overrides/$sizeGroup").expectStatus().isNoContent
            client
                .delete()
                .uri("/api/v1/admin/option-groups/$sizeGroup/scheduled-changes/options")
                .exchange()
                .expectStatus()
                .isNoContent

            listProduct().expectBody().json("[]", JsonCompareMode.STRICT)
            listOptionGroup(sizeGroup).expectBody().json("[]", JsonCompareMode.STRICT)
        }

        @Test
        fun `태그 예약을 취소해도 등록할 때 만든 태그는 남는다`() =
            runTest {
                putProduct("tags", """{"effectiveDate": "2026-10-01", "value": ["신메뉴"]}""").expectStatus().isOk

                deleteProduct("tags").expectStatus().isNoContent

                assertEquals(1, count("SELECT count(*) FROM tags WHERE name = '신메뉴'"))
            }

        @Test
        fun `대기 예약이 없으면 404 NO_PENDING_SCHEDULE`() {
            deleteProduct("name").expectProblem(404, "NO_PENDING_SCHEDULE")
        }

        @Test
        fun `이미 취소한 예약을 다시 취소하면 404 NO_PENDING_SCHEDULE`() {
            putProduct("name", """{"effectiveDate": "2026-10-01", "value": "라떼"}""").expectStatus().isOk
            deleteProduct("name").expectStatus().isNoContent

            deleteProduct("name").expectProblem(404, "NO_PENDING_SCHEDULE")
        }
    }

    @Nested
    @DisplayName("경로의 필드와 대상")
    inner class FieldAndTarget {
        @ParameterizedTest
        @ValueSource(strings = ["status", "tracksInventory", "option-overrides", "option-overrides/1/extra", "name/extra", "options"])
        fun `예약할 수 없는 상품 필드는 404 UNKNOWN_SCHEDULE_FIELD`(field: String) {
            putProduct(field, """{"effectiveDate": "2026-10-01", "value": null}""").expectProblem(404, "UNKNOWN_SCHEDULE_FIELD")
            deleteProduct(field).expectProblem(404, "UNKNOWN_SCHEDULE_FIELD")
        }

        @ParameterizedTest
        @ValueSource(strings = ["name", "required", "selectionType"])
        fun `예약할 수 없는 옵션 그룹 필드는 404 UNKNOWN_SCHEDULE_FIELD`(field: String) {
            client
                .put()
                .uri("/api/v1/admin/option-groups/$sizeGroup/scheduled-changes/$field")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"effectiveDate": "2026-10-01", "value": "사이즈"}""")
                .exchange()
                .expectProblem(404, "UNKNOWN_SCHEDULE_FIELD")
        }

        @Test
        fun `옵션 그룹별 예외 경로의 옵션 그룹 ID가 숫자가 아니면 400 INVALID_REQUEST`() {
            putProduct("option-overrides/abc", """{"effectiveDate": "2026-10-01", "value": []}""")
                .expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `상품이 없으면 404 PRODUCT_NOT_FOUND`() {
            client
                .get()
                .uri("/api/v1/admin/products/999/scheduled-changes")
                .exchange()
                .expectProblem(404, "PRODUCT_NOT_FOUND")
            client
                .put()
                .uri("/api/v1/admin/products/999/scheduled-changes/name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"effectiveDate": "2026-10-01", "value": "라떼"}""")
                .exchange()
                .expectProblem(404, "PRODUCT_NOT_FOUND")
        }

        @Test
        fun `옵션 그룹이 없으면 404 OPTION_GROUP_NOT_FOUND`() {
            listOptionGroup(999).expectProblem(404, "OPTION_GROUP_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("요청 형식 오류는 400 INVALID_REQUEST")
    inner class MalformedRequest {
        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource(
            delimiter = '|',
            value = [
                "name | 123",
                "name | null",
                "category | 1.5",
                "basePrice | '\"4800\"'",
                "description | 12",
                "groups | 1",
                "optionGroupLinks | '[\"a\"]'",
                "storeScope | '{\"kind\": \"SOME\"}'",
                "storeScope | '{\"kind\": \"LIMITED\"}'",
                "storeScope | '{\"kind\": \"ALL\", \"targetStoreIds\": [101]}'",
                "activation | true",
                "tags | '[1]'",
                "option-overrides/1 | '[{\"optionKey\": \"TALL\", \"type\": \"DISCOUNT\"}]'",
                "option-overrides/1 | '[{\"optionKey\": \"TALL\", \"type\": \"EXCLUDE\", \"price\": 100}]'",
                "option-overrides/1 | '[{\"optionKey\": \"TALL\", \"type\": \"PRICE\"}]'",
            ],
        )
        fun `필드에 맞지 않는 value는 거부한다`(
            field: String,
            value: String,
        ) = runTest {
            putProduct(field, """{"effectiveDate": "2026-10-01", "value": $value}""").expectProblem(400, "INVALID_REQUEST")

            assertEquals(0, count("SELECT count(*) FROM scheduled_changes"))
        }

        @Test
        fun `옵션 목록이 배열이 아니면 거부한다`() {
            client
                .put()
                .uri("/api/v1/admin/option-groups/$sizeGroup/scheduled-changes/options")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"effectiveDate": "2026-10-01", "value": {"optionKey": "TALL"}}""")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `문제가 된 값의 위치를 알려 준다`() {
            putProduct(
                "option-overrides/$sizeGroup",
                """{"effectiveDate": "2026-10-01", "value": [{"optionKey": "TALL", "type": "EXCLUDE"}, {"optionKey": "GRANDE", "type": "DISCOUNT"}]}""",
            ).expectProblem(400, "INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 본문의 'value[1].type' 값이 없거나 형식이 맞지 않습니다")
        }

        @Test
        fun `적용일이 없으면 거부한다`() {
            putProduct("name", """{"value": "라떼"}""")
                .expectProblem(400, "INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 본문의 'effectiveDate' 값이 없거나 형식이 맞지 않습니다")
        }
    }

    @Nested
    @DisplayName("등록 시점의 거부")
    inner class RegistrationRejection {
        @Test
        fun `오늘 날짜는 400 INVALID_EFFECTIVE_DATE`() =
            runTest {
                putProduct("basePrice", """{"effectiveDate": "2026-09-21", "value": 4800}""")
                    .expectProblem(400, "INVALID_EFFECTIVE_DATE")

                assertEquals(0, count("SELECT count(*) FROM scheduled_changes"))
            }

        @Test
        fun `없는 카테고리는 404 CATEGORY_NOT_FOUND`() {
            putProduct("category", """{"effectiveDate": "2026-10-01", "value": 999}""").expectProblem(404, "CATEGORY_NOT_FOUND")
        }

        @Test
        fun `대분류는 400 CATEGORY_NOT_ASSIGNABLE`() {
            putProduct("category", """{"effectiveDate": "2026-10-01", "value": $beverage}""")
                .expectProblem(400, "CATEGORY_NOT_ASSIGNABLE")
        }

        @Test
        fun `음수 기준가는 400 INVALID_MONEY_AMOUNT`() {
            putProduct("basePrice", """{"effectiveDate": "2026-10-01", "value": -1}""").expectProblem(400, "INVALID_MONEY_AMOUNT")
        }

        @Test
        fun `빈 옵션 목록은 422 EMPTY_OPTION_GROUP`() =
            runTest {
                client
                    .put()
                    .uri("/api/v1/admin/option-groups/$sizeGroup/scheduled-changes/options")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"effectiveDate": "2026-10-01", "value": []}""")
                    .exchange()
                    .expectProblem(422, "EMPTY_OPTION_GROUP")

                assertEquals(0, count("SELECT count(*) FROM scheduled_changes"))
            }
    }

    private fun putProduct(
        field: String,
        body: String,
    ): WebTestClient.ResponseSpec =
        client
            .put()
            .uri("/api/v1/admin/products/$product/scheduled-changes/$field")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()

    private fun deleteProduct(field: String): WebTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/v1/admin/products/$product/scheduled-changes/$field")
            .exchange()

    private fun listProduct(): WebTestClient.ResponseSpec =
        client
            .get()
            .uri("/api/v1/admin/products/$product/scheduled-changes")
            .exchange()

    private fun listOptionGroup(optionGroupId: Long): WebTestClient.ResponseSpec =
        client
            .get()
            .uri("/api/v1/admin/option-groups/$optionGroupId/scheduled-changes")
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

    private fun scheduledChangeJson(
        field: String,
        value: String,
        effectiveDate: String,
    ) = """{"field": "$field", "value": $value, "effectiveDate": "$effectiveDate", "status": "PENDING"}"""

    // 테스트 데이터의 ID는 준비할 때 정해지므로 케이스에는 이름으로 적고 여기서 바꾼다.
    private fun resolve(template: String): String =
        template
            .replace("{coffee}", coffee.toString())
            .replace("{size}", sizeGroup.toString())
            .replace("{shot}", shotGroup.toString())
            .replace("{group}", seasonGroup.toString())

    private suspend fun insertOptionGroup(
        name: String,
        vararg optionKeys: String,
    ): Long {
        execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('$name', 'SINGLE', true)")
        val id = count("SELECT max(id) FROM option_groups")
        optionKeys.forEachIndexed { index, key ->
            execute(
                "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                    "VALUES ($id, '$key', '$key', 0, $index)",
            )
        }
        return id
    }

    companion object {
        // 경로의 필드, 요청 value, 응답 value. 태그(이름 → ID)와 옵션 목록(옵션 그룹 대상)은 따로 확인한다.
        @JvmStatic
        fun productFieldValues(): List<Array<String>> =
            listOf(
                arrayOf("name", "\"라떼\"", "\"라떼\""),
                arrayOf("category", "{coffee}", "{coffee}"),
                arrayOf("description", "\"고소한 라떼\"", "\"고소한 라떼\""),
                arrayOf("description", "null", "null"),
                arrayOf("image", "\"https://cdn.dozycoffee.com/latte.png\"", "\"https://cdn.dozycoffee.com/latte.png\""),
                arrayOf("image", "null", "null"),
                arrayOf("basePrice", "4800", "4800"),
                arrayOf("groups", "[{group}]", "[{group}]"),
                arrayOf(
                    "storeScope",
                    """{"kind": "LIMITED", "targetStoreIds": [102, 101]}""",
                    """{"kind": "LIMITED", "targetStoreIds": [101, 102]}""",
                ),
                arrayOf("storeScope", """{"kind": "LIMITED", "targetStoreIds": []}""", """{"kind": "LIMITED", "targetStoreIds": []}"""),
                arrayOf("storeScope", """{"kind": "ALL"}""", """{"kind": "ALL", "targetStoreIds": []}"""),
                arrayOf("activation", "null", "null"),
                arrayOf("discontinuation", "null", "null"),
                arrayOf("optionGroupLinks", "[{shot}, {size}]", "[{shot}, {size}]"),
                arrayOf(
                    "option-overrides/{size}",
                    """[{"optionKey": "GRANDE", "type": "PRICE", "price": 700}, {"optionKey": "TALL", "type": "EXCLUDE"}]""",
                    """[{"optionKey": "GRANDE", "type": "PRICE", "price": 700}, {"optionKey": "TALL", "type": "EXCLUDE", "price": null}]""",
                ),
            )
    }
}
