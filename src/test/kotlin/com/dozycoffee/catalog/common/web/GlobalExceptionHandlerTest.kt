package com.dozycoffee.catalog.common.web

import com.dozycoffee.catalog.core.ErrorType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

// 응답 본문 형식은 ProblemResponseTest가 실제 요청으로 확인한다.
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {
    @ParameterizedTest
    @CsvSource(
        "INVALID_INPUT, BAD_REQUEST",
        "NOT_FOUND, NOT_FOUND",
        "CONFLICT, CONFLICT",
        "BUSINESS_RULE_VIOLATION, UNPROCESSABLE_CONTENT",
    )
    fun `ErrorType에 따라 HTTP 상태 코드를 결정한다`(
        type: ErrorType,
        expected: HttpStatus,
    ) {
        assertEquals(expected, type.toHttpStatus())
    }
}
