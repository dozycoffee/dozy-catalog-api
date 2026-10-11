package com.dozycoffee.catalog.common.security

import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.catalog.support.IntegrationTest
import com.dozycoffee.webprobe.WebProbeController
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 관리 콘솔(브라우저)이 본사 API를 부를 수 있게 하는 CORS(docs/api/README.md CORS).
// 허용 출처는 테스트에서도 로컬 기본값(http://localhost:3000)을 쓴다.
// 요청은 호스트가 있는 절대 URL로 보낸다. 서버 없이 붙는 WebTestClient는 상대 경로를 그대로 넘기는데,
// 호스트가 없는 요청은 CORS 처리기가 출처를 비교하지 못해 거부한다(실제 서버의 요청에는 항상 호스트가 있다).
@DisplayName("CORS")
@AutoConfigureWebTestClient
@Import(WebProbeController::class)
class CorsTest : IntegrationTest() {
    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var tokens: DozyTestTokens

    private fun preflight(
        path: String,
        origin: String,
    ): WebTestClient.ResponseSpec =
        client
            .options()
            .uri(BASE + path)
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name())
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type, if-match")
            .exchange()

    @Nested
    @DisplayName("본사 API")
    inner class AdminApi {
        @Test
        fun `허용한 출처의 사전 요청은 토큰 없이 통과한다`() {
            val headers =
                preflight("/api/v1/admin/probe/versioned", ALLOWED)
                    .expectStatus()
                    .isOk
                    .returnResult(Void::class.java)
                    .responseHeaders

            assertEquals(ALLOWED, headers.accessControlAllowOrigin)
            assertTrue(HttpMethod.PUT in headers.accessControlAllowMethods)
            assertEquals(
                setOf("authorization", "content-type", "if-match"),
                headers.accessControlAllowHeaders.map { it.lowercase() }.toSet(),
            )
            assertEquals(3600, headers.accessControlMaxAge)
            // 쿠키를 쓰지 않으므로 credentials를 허용하지 않는다.
            assertNull(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
        }

        @Test
        fun `허용하지 않은 출처의 사전 요청은 거부한다`() {
            val headers =
                preflight("/api/v1/admin/probe/versioned", "https://evil.example.com")
                    .expectStatus()
                    .isForbidden
                    .returnResult(Void::class.java)
                    .responseHeaders

            assertNull(headers.accessControlAllowOrigin)
        }

        @Test
        fun `실제 요청의 응답은 ETag를 브라우저가 읽을 수 있게 연다`() {
            val headers =
                client
                    .put()
                    .uri("$BASE/api/v1/admin/probe/versioned")
                    .header(HttpHeaders.ORIGIN, ALLOWED)
                    .header(HttpHeaders.IF_MATCH, "\"3\"")
                    .headers { it.setBearerAuth(tokens.issue(roles = listOf("catalog:admin"))) }
                    .exchange()
                    .expectStatus()
                    .isOk
                    .returnResult(Void::class.java)
                    .responseHeaders

            assertEquals(ALLOWED, headers.accessControlAllowOrigin)
            assertEquals(
                setOf(HttpHeaders.ETAG, HttpHeaders.LOCATION, "X-Trace-Id").map { it.lowercase() }.toSet(),
                headers.accessControlExposeHeaders.map { it.lowercase() }.toSet(),
            )
            assertNotNull(headers.eTag)
        }
    }

    @Test
    fun `내부 API에는 CORS를 열지 않는다`() {
        // Store 서비스가 서버에서 부르는 API라 CORS 설정이 없다. 사전 요청에 허용 헤더가 없으므로 브라우저가 실제 요청을 보내지 않는다.
        val headers =
            preflight("/api/v1/internal/probe", ALLOWED)
                .returnResult(Void::class.java)
                .responseHeaders

        assertNull(headers.accessControlAllowOrigin)
    }

    private companion object {
        const val BASE = "http://catalog.local"
        const val ALLOWED = "http://localhost:3000"
    }
}
