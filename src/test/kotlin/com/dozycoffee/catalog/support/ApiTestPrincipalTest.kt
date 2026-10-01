package com.dozycoffee.catalog.support

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.WithDozyPrincipal
import com.dozycoffee.webprobe.WebProbeController
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

// 내부 API 테스트가 쓰는 방식대로 ApiTest의 기본 요청자(본사 직원)를 Store 서비스로 바꿀 수 있는지 확인한다.
@DisplayName("ApiTest 요청자 바꾸기")
@Import(WebProbeController::class)
@WithDozyPrincipal(type = PrincipalType.SYSTEM, roles = ["catalog:store_agent"])
class ApiTestPrincipalTest : ApiTest() {
    @Test
    fun `클래스에 붙인 요청자가 기본값을 대신한다`() {
        client
            .get()
            .uri("/api/v1/internal/probe")
            .exchange()
            .expectStatus()
            .isOk
        client
            .get()
            .uri("/api/v1/admin/probe")
            .exchange()
            .expectStatus()
            .isForbidden
    }
}
