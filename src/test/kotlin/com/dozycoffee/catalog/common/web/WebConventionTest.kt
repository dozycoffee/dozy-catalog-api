package com.dozycoffee.catalog.common.web

import com.dozycoffee.catalog.support.ApiTest
import com.dozycoffee.webprobe.WebProbeController
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// API 공통 규약(docs/api/README.md): 요청 오류, 버전 헤더, 목록 파라미터, JSON 형식.
// 도메인 예외의 응답 형식은 ProblemResponseTest가 확인한다.
@DisplayName("API 공통 규약")
@Import(WebProbeController::class)
class WebConventionTest : ApiTest() {
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

    private fun postBody(json: String): WebTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/v1/admin/probe/body")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()

    @Nested
    @DisplayName("요청 본문·파라미터 오류는 400 INVALID_REQUEST")
    inner class InvalidRequest {
        @Test
        fun `올바른 본문은 그대로 읽는다`() {
            postBody("""{"name": "아메리카노", "count": 2, "kind": "FIRST"}""")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("아메리카노")
        }

        @Test
        fun `JSON 형식이 깨졌으면 거부한다`() {
            postBody("""{"name": """).expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `필수 필드가 없으면 그 필드를 알려 준다`() {
            postBody("""{"count": 2, "kind": "FIRST"}""")
                .expectProblem(400, "INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 본문의 'name' 값이 없거나 형식이 맞지 않습니다")
        }

        @Test
        fun `필드 타입이 다르면 거부한다`() {
            postBody("""{"name": "아메리카노", "count": "two", "kind": "FIRST"}""")
                .expectProblem(400, "INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 본문의 'count' 값이 없거나 형식이 맞지 않습니다")
        }

        @Test
        fun `허용되지 않는 enum 값이면 거부한다`() {
            postBody("""{"name": "아메리카노", "count": 2, "kind": "THIRD"}""")
                .expectProblem(400, "INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 본문의 'kind' 값이 없거나 형식이 맞지 않습니다")
        }

        @Test
        fun `오류 설명에 내부 클래스 이름을 담지 않는다`() {
            val body =
                postBody("""{"count": 2, "kind": "FIRST"}""")
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody
            assertFalse(assertNotNull(body).contains("com.dozycoffee"), body)
        }

        @Test
        fun `경로 변수 타입이 다르면 거부한다`() {
            client
                .get()
                .uri("/api/v1/admin/probe/items/abc")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `쿼리 파라미터 타입이 다르면 거부한다`() {
            client
                .get()
                .uri("/api/v1/admin/probe/list?page=abc")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
        }
    }

    @Nested
    @DisplayName("목록 파라미터")
    inner class ListParameters {
        @Test
        fun `페이지를 주지 않으면 0페이지 20개다`() {
            client
                .get()
                .uri("/api/v1/admin/probe/list")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.page")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(20)
                .jsonPath("$.totalElements")
                .isEqualTo(2)
                .jsonPath("$.totalPages")
                .isEqualTo(1)
        }

        @Test
        fun `ids는 쉼표로 구분해 받는다`() {
            client
                .get()
                .uri("/api/v1/admin/probe/list?ids=15,12, 20")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content")
                .isEqualTo(listOf(12, 15, 20))
        }

        @ParameterizedTest
        @ValueSource(strings = ["size=0", "size=101", "page=-1", "ids=1,abc", "ids=0"])
        fun `범위나 형식을 벗어난 값은 거부한다`(query: String) {
            client
                .get()
                .uri("/api/v1/admin/probe/list?$query")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `ids가 100개를 넘으면 거부한다`() {
            val ids = (1..101).joinToString(",")
            client
                .get()
                .uri("/api/v1/admin/probe/list?ids=$ids")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
        }
    }

    @Nested
    @DisplayName("낙관적 잠금 버전 (If-Match / ETag)")
    inner class VersionHeader {
        private fun put(ifMatch: String?): WebTestClient.ResponseSpec =
            client
                .put()
                .uri("/api/v1/admin/probe/versioned")
                .headers { headers -> ifMatch?.let { headers.set(HttpHeaders.IF_MATCH, it) } }
                .exchange()

        @Test
        fun `If-Match로 받은 버전을 쓰고 응답에 새 버전을 ETag로 담는다`() {
            put("\"3\"")
                .expectStatus()
                .isOk
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"4\"")
                .expectBody()
                .jsonPath("$.version")
                .isEqualTo(4)
        }

        @Test
        fun `If-Match가 없으면 428 VERSION_REQUIRED`() {
            put(null).expectProblem(428, "VERSION_REQUIRED")
        }

        @ParameterizedTest
        @ValueSource(strings = ["3", "W/\"3\"", "*", "\"abc\""])
        fun `형식이 틀린 If-Match는 400`(ifMatch: String) {
            put(ifMatch).expectProblem(400, "INVALID_REQUEST")
        }
    }

    @Nested
    @DisplayName("HTTP 프로토콜 오류와 서버 오류")
    inner class OtherErrors {
        @Test
        fun `없는 경로는 404 NOT_FOUND`() {
            client
                .get()
                .uri("/api/v1/admin/no-such-resource")
                .exchange()
                .expectProblem(404, "NOT_FOUND")
        }

        @Test
        fun `허용하지 않는 메서드는 405 METHOD_NOT_ALLOWED`() {
            client
                .delete()
                .uri("/api/v1/admin/probe/list")
                .exchange()
                .expectProblem(405, "METHOD_NOT_ALLOWED")
        }

        @Test
        fun `지원하지 않는 Content-Type은 415 UNSUPPORTED_MEDIA_TYPE`() {
            client
                .post()
                .uri("/api/v1/admin/probe/body")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("name=아메리카노")
                .exchange()
                .expectProblem(415, "UNSUPPORTED_MEDIA_TYPE")
        }

        @Test
        fun `처리되지 않은 예외는 500 INTERNAL_ERROR이고 내부 정보를 담지 않는다`() {
            val body =
                client
                    .get()
                    .uri("/api/v1/admin/probe/internal-error")
                    .exchange()
                    .expectProblem(500, "INTERNAL_ERROR")
                    .jsonPath("$.detail")
                    .isEqualTo("서버 오류가 발생했습니다")
                    .returnResult()
                    .responseBody
            assertFalse(String(assertNotNull(body)).contains("secret_table"))
        }

        @Test
        fun `메서드 보안의 거부는 500으로 바꾸지 않고 403 FORBIDDEN으로 둔다`() {
            client
                .get()
                .uri("/api/v1/admin/probe/forbidden-by-method")
                .exchange()
                .expectProblem(403, "FORBIDDEN")
        }
    }

    @Nested
    @DisplayName("JSON 형식")
    inner class JsonFormat {
        @Test
        fun `값이 없는 필드는 null로, 날짜·시각은 ISO-8601로, 빈 목록은 빈 배열로 준다`() {
            val body =
                client
                    .get()
                    .uri("/api/v1/admin/probe/json")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
                    .responseBody
            // jsonPath는 null 값과 없는 필드를 구분하지 못해 본문에서 직접 확인한다.
            assertTrue(String(assertNotNull(body)).contains("\"description\":null"), String(body))

            client
                .get()
                .uri("/api/v1/admin/probe/json")
                .exchange()
                .expectBody()
                .jsonPath("$.effectiveDate")
                .isEqualTo("2026-10-01")
                .jsonPath("$.occurredAt")
                .isEqualTo("2026-10-01T00:00:00Z")
                .jsonPath("$.kind")
                .isEqualTo("FIRST")
                .jsonPath("$.tags")
                .isEmpty
        }
    }
}
