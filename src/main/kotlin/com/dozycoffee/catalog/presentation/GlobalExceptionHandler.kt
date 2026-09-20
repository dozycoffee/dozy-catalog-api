package com.dozycoffee.catalog.presentation

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.presentation.dto.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(DomainException::class)
    fun handleDomainException(e: DomainException): ResponseEntity<ErrorResponse> {
        logger.warn(e) { "도메인 규칙 위반: ${e.errorCode.code}" }
        return ResponseEntity
            .status(e.errorCode.type.toHttpStatus())
            .body(ErrorResponse(code = e.errorCode.code, message = e.message ?: e.errorCode.code))
    }
}

// else 없이 모든 ErrorType을 나열해서, ErrorType이 추가되면 컴파일 단계에서 매핑 누락이 드러나게 한다.
internal fun ErrorType.toHttpStatus(): HttpStatus =
    when (this) {
        ErrorType.INVALID_INPUT -> HttpStatus.BAD_REQUEST
        ErrorType.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorType.CONFLICT -> HttpStatus.CONFLICT
        ErrorType.BUSINESS_RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT
    }
