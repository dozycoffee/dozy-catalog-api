package com.dozycoffee.catalog.common.web

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.core.VersionConflictException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(DomainException::class)
    fun handleDomainException(
        e: DomainException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> {
        logger.warn(e) { "도메인 규칙 위반: ${e.errorCode.code}" }
        return ProblemResponses.of(
            status = e.errorCode.type.toHttpStatus(),
            code = e.errorCode.code,
            detail = e.message,
            exchange = exchange,
            extensions = extensionsOf(e),
        )
    }

    // 오류 코드마다 클라이언트가 다시 판단하는 데 필요한 값. 낙관적 잠금 충돌은 최신 버전을 알려 준다(ADR-0013).
    private fun extensionsOf(e: DomainException): Map<String, Any?> =
        when (e) {
            is VersionConflictException -> mapOf("currentVersion" to e.currentVersion)
            else -> emptyMap()
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
