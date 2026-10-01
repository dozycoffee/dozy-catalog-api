package com.dozycoffee.catalog.common.web

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.core.VersionConflictException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import tools.jackson.core.JacksonException

private val logger = KotlinLogging.logger {}

// 모든 오류를 Problem Details로 응답한다(docs/api/README.md 오류 응답). 401·403은 dozy-auth 스타터가 같은 형식으로 돌려준다.
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

    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(
        e: InvalidRequestException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> = ProblemResponses.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, e.message, exchange)

    @ExceptionHandler(VersionRequiredException::class)
    fun handleVersionRequired(
        e: VersionRequiredException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> = ProblemResponses.of(HttpStatus.PRECONDITION_REQUIRED, VERSION_REQUIRED, e.message, exchange)

    // 본문을 읽을 수 없음, 필수 필드 누락, 타입 불일치, 허용되지 않는 enum 값, 경로·쿼리 변환 실패.
    @ExceptionHandler(ServerWebInputException::class)
    fun handleInput(
        e: ServerWebInputException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> = ProblemResponses.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, describe(e), exchange)

    // 없는 경로(404), 허용하지 않는 메서드(405), 지원하지 않는 Content-Type(415) 등 HTTP 프로토콜 수준의 오류.
    // 도메인 오류와 섞이지 않도록 code는 HTTP 상태 이름(NOT_FOUND 등)을 쓴다.
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        e: ResponseStatusException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> {
        val status = HttpStatus.resolve(e.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return ProblemResponses.of(status, status.name, e.reason, exchange)
    }

    // 인가 실패는 여기서 삼키지 않고 Spring Security로 넘겨 스타터의 401·403 응답을 쓰게 한다.
    @ExceptionHandler(AccessDeniedException::class, AuthenticationException::class)
    fun rethrowSecurity(e: RuntimeException): Nothing = throw e

    // 그 밖의 모든 예외는 서버 버그다(require/check 위반 포함, docs/architecture/exception.md).
    // 원인은 로그에만 남기고 응답에는 내부 정보(메시지, 스택, SQL, 클래스 이름)를 담지 않는다.
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        e: Exception,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> {
        val response = ProblemResponses.of(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, "서버 오류가 발생했습니다", exchange)
        logger.error(e) { "처리되지 않은 예외: ${exchange.request.method} ${exchange.request.path} (traceId=${traceIdOf(response)})" }
        return response
    }

    // 오류 코드마다 클라이언트가 다시 판단하는 데 필요한 값. 낙관적 잠금 충돌은 최신 버전을 알려 준다(ADR-0013).
    private fun extensionsOf(e: DomainException): Map<String, Any?> =
        when (e) {
            is VersionConflictException -> mapOf("currentVersion" to e.currentVersion)
            else -> emptyMap()
        }

    // 어느 필드나 파라미터가 문제인지만 알려 준다. Jackson 메시지에는 클래스 이름이 섞여 있어 그대로 내보내지 않는다.
    private fun describe(e: ServerWebInputException): String {
        val field =
            generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<JacksonException>()
                .firstOrNull()
                ?.path
                ?.joinToString(".") { reference -> reference.propertyName ?: "[${reference.index}]" }
                ?.takeIf { it.isNotEmpty() }
        val parameter = e.methodParameter?.parameterName
        return when {
            field != null -> "요청 본문의 '$field' 값이 없거나 형식이 맞지 않습니다"
            e.cause is JacksonException || e.cause?.cause is JacksonException -> "요청 본문을 읽을 수 없습니다"
            parameter != null -> "'$parameter' 값이 없거나 형식이 맞지 않습니다"
            else -> "요청을 읽을 수 없습니다"
        }
    }

    private fun traceIdOf(response: ResponseEntity<ProblemDetail>): String? = response.headers.getFirst(TraceIds.HEADER)

    private companion object {
        const val INVALID_REQUEST = "INVALID_REQUEST"
        const val VERSION_REQUIRED = "VERSION_REQUIRED"
        const val INTERNAL_ERROR = "INTERNAL_ERROR"
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
