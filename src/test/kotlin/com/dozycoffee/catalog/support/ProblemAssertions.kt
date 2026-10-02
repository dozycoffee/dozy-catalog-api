package com.dozycoffee.catalog.support

import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

// API 테스트가 함께 쓰는 오류 응답 확인. 클라이언트는 code로만 분기하므로 상태와 code를 본다(docs/api/README.md 오류 응답).
internal fun WebTestClient.ResponseSpec.expectProblem(
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
