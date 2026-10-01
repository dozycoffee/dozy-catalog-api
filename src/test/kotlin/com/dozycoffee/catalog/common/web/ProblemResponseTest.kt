package com.dozycoffee.catalog.common.web

import com.dozycoffee.auth.test.WithDozyPrincipal
import com.dozycoffee.catalog.support.IntegrationTest
import com.dozycoffee.webprobe.WebProbeController
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 도메인 예외의 HTTP 오류 응답(RFC 9457 Problem Details, docs/api/README.md 오류 응답).
@DisplayName("오류 응답 형식")
@AutoConfigureWebTestClient
@Import(WebProbeController::class)
@WithDozyPrincipal(roles = ["catalog:admin"])
class ProblemResponseTest : IntegrationTest() {
    @Autowired
    private lateinit var client: WebTestClient

    @ParameterizedTest
    @CsvSource(
        "INVALID_INPUT, 400",
        "NOT_FOUND, 404",
        "CONFLICT, 409",
        "BUSINESS_RULE_VIOLATION, 422",
    )
    fun `ErrorType에 따라 상태 코드가 정해진다`(
        type: String,
        status: Int,
    ) {
        client
            .get()
            .uri("/api/v1/admin/probe/domain-error/$type")
            .exchange()
            .expectStatus()
            .isEqualTo(status)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(status)
    }

    @Test
    fun `도메인 예외는 Problem Details 형식으로 응답한다`() {
        val result =
            client
                .get()
                .uri("/api/v1/admin/probe/domain-error/CONFLICT")
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://docs.dozycoffee.com/errors/probe-failed")
                .jsonPath("$.title")
                .isEqualTo("Probe failed")
                .jsonPath("$.detail")
                .isEqualTo("프로브 메시지")
                .jsonPath("$.instance")
                .isEqualTo("/api/v1/admin/probe/domain-error/CONFLICT")
                .jsonPath("$.code")
                .isEqualTo("PROBE_FAILED")
                .returnResult()

        val traceIdHeader = assertNotNull(result.responseHeaders.getFirst(TraceIds.HEADER))
        val body = String(assertNotNull(result.responseBody))
        assertTrue(body.contains("\"traceId\":\"$traceIdHeader\""), body)
    }

    @Test
    fun `요청의 X-Trace-Id를 응답의 traceId로 그대로 쓴다`() {
        client
            .get()
            .uri("/api/v1/admin/probe/domain-error/NOT_FOUND")
            .header(TraceIds.HEADER, "abc-123")
            .exchange()
            .expectHeader()
            .valueEquals(TraceIds.HEADER, "abc-123")
            .expectBody()
            .jsonPath("$.traceId")
            .isEqualTo("abc-123")
    }

    @Test
    fun `형식에 맞지 않는 X-Trace-Id는 쓰지 않고 새로 만든다`() {
        val result =
            client
                .get()
                .uri("/api/v1/admin/probe/domain-error/NOT_FOUND")
                .header(TraceIds.HEADER, "bad id!")
                .exchange()
                .expectBody()
                .returnResult()

        val traceId = assertNotNull(result.responseHeaders.getFirst(TraceIds.HEADER))
        assertTrue(Regex("[0-9a-f]{32}").matches(traceId), traceId)
    }

    @Test
    fun `낙관적 잠금 충돌은 현재 버전을 함께 담는다`() {
        client
            .get()
            .uri("/api/v1/admin/probe/version-conflict")
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("VERSION_CONFLICT")
            .jsonPath("$.currentVersion")
            .isEqualTo(5)
    }

    @Test
    fun `title은 code에서 만든다`() {
        assertEquals("Product not deletable", ProblemResponses.titleOf("PRODUCT_NOT_DELETABLE"))
    }
}
