package com.dozycoffee.catalog.presentation

import com.dozycoffee.catalog.domain.shared.DomainException
import com.dozycoffee.catalog.domain.shared.ErrorCode
import com.dozycoffee.catalog.domain.shared.ErrorType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    private class TestErrorCode(
        override val type: ErrorType,
    ) : ErrorCode {
        override val code: String = "TEST_ERROR"
    }

    private class TestDomainException(
        type: ErrorType,
    ) : DomainException(TestErrorCode(type), "테스트 메시지")

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
        val response = handler.handleDomainException(TestDomainException(type))

        assertEquals(expected, response.statusCode)
    }

    @Test
    fun `응답 본문에 code와 message를 담는다`() {
        val response = handler.handleDomainException(TestDomainException(ErrorType.CONFLICT))

        assertEquals("TEST_ERROR", response.body?.code)
        assertEquals("테스트 메시지", response.body?.message)
    }
}
