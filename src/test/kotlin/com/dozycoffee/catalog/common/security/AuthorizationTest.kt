package com.dozycoffee.catalog.common.security

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.auth.test.WithDozyPrincipal
import com.dozycoffee.catalog.support.IntegrationTest
import com.dozycoffee.webprobe.WebProbeController
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration
import java.time.Instant
import kotlin.test.assertTrue

// 호출자별 경로 인가와 토큰 검증(docs/api/README.md 경로와 인가, docs/adr/0018).
// 토큰 검증은 dozy-auth 스타터가 하므로, 여기서는 Catalog 설정(audience·realm·경로 규칙)이 의도대로 걸렸는지만 확인한다.
@DisplayName("경로 인가와 토큰 검증")
@AutoConfigureWebTestClient
@Import(WebProbeController::class)
class AuthorizationTest : IntegrationTest() {
    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var tokens: DozyTestTokens

    private fun get(
        path: String,
        token: String? = null,
    ): WebTestClient.ResponseSpec =
        client
            .get()
            .uri(path)
            .headers { headers -> token?.let { headers.setBearerAuth(it) } }
            .exchange()

    @Nested
    @DisplayName("호출자별 경로")
    inner class Paths {
        @Test
        fun `본사 직원은 본사 API를 부를 수 있다`() {
            get("/api/v1/admin/probe", tokens.issue(roles = listOf("catalog:admin"))).expectStatus().isOk
        }

        @Test
        fun `Store 서비스는 내부 API를 부를 수 있다`() {
            val token = tokens.issue(type = PrincipalType.SYSTEM, roles = listOf("catalog:store_agent"))

            get("/api/v1/internal/probe", token).expectStatus().isOk
        }

        @Test
        fun `Store 서비스는 본사 API를 부를 수 없다`() {
            val token = tokens.issue(type = PrincipalType.SYSTEM, roles = listOf("catalog:store_agent"))

            get("/api/v1/admin/probe", token).expectStatus().isForbidden
        }

        @Test
        fun `본사 직원은 내부 API를 부를 수 없다`() {
            get("/api/v1/internal/probe", tokens.issue(roles = listOf("catalog:admin"))).expectStatus().isForbidden
        }

        @Test
        fun `Catalog 역할이 없으면 본사 API를 부를 수 없다`() {
            get("/api/v1/admin/probe", tokens.issue(roles = emptyList())).expectStatus().isForbidden
        }

        @Test
        fun `헬스 체크는 토큰 없이 부를 수 있다`() {
            get("/actuator/health").expectStatus().isOk
        }

        @Test
        fun `Prometheus 지표는 토큰 없이 부를 수 있다`() {
            get("/actuator/prometheus")
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .value { body ->
                    val text = body.orEmpty()
                    assertTrue(text.contains("jvm_memory_used_bytes"))
                    // 부하 테스트에서 풀 포화를 보려면 필요한 지표라, 사라지면 알아챌 수 있게 확인한다.
                    assertTrue(text.contains("r2dbc_pool_pending_connections"))
                    assertTrue(text.contains("""application="dozy-catalog-api""""))
                    assertTrue(text.contains("dozy_catalog_build_info"))
                }
        }

        @Test
        fun `info는 토큰이 필요하다`() {
            get("/actuator/info").expectStatus().isUnauthorized
        }

        @Test
        @WithDozyPrincipal(roles = ["catalog:admin"])
        fun `API 테스트는 WithDozyPrincipal로 인증된 사용자를 만들 수 있다`() {
            get("/api/v1/admin/probe").expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("토큰 검증 실패는 401")
    inner class InvalidToken {
        private val adminRoles = listOf("catalog:admin")

        @Test
        fun `토큰이 없으면 401이고 오류 형식은 Problem Details다`() {
            get("/api/v1/admin/probe")
                .expectStatus()
                .isUnauthorized
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader()
                .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")
        }

        @Test
        fun `만료된 토큰은 401`() {
            val issuedAt = Instant.now().minus(Duration.ofMinutes(30))
            val token = tokens.issue(roles = adminRoles, issuedAt = issuedAt, expiresAt = issuedAt.plus(Duration.ofMinutes(10)))

            get("/api/v1/admin/probe", token).expectStatus().isUnauthorized
        }

        @Test
        fun `다른 서비스용 토큰(aud)은 401`() {
            val token = tokens.issue(roles = listOf("wms:inbound_manager"), audience = listOf("wms"))

            get("/api/v1/admin/probe", token).expectStatus().isUnauthorized
        }

        @Test
        fun `가맹점주 토큰(realm partner)은 401`() {
            val token = tokens.issue(type = PrincipalType.PARTNER, audience = listOf("catalog"))

            get("/api/v1/internal/probe", token).expectStatus().isUnauthorized
        }

        @Test
        fun `믿지 않는 키로 서명한 토큰은 401`() {
            val token = tokens.issue(roles = adminRoles, signedBy = DozyTestTokens.Key.UNTRUSTED)

            get("/api/v1/admin/probe", token).expectStatus().isUnauthorized
        }
    }
}
