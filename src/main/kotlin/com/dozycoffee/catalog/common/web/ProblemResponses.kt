package com.dozycoffee.catalog.common.web

import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ServerWebExchange
import java.net.URI
import java.security.SecureRandom
import java.util.HexFormat

// 오류 응답(RFC 9457 Problem Details, docs/api/README.md 오류 응답). dozy-auth 스타터가 돌려주는 401·403과
// 같은 형식이라 한 API의 오류 형식이 하나로 유지된다. 클라이언트는 code로만 분기한다.
object ProblemResponses {
    private const val TYPE_BASE = "https://docs.dozycoffee.com/errors/"

    fun of(
        status: HttpStatusCode,
        code: String,
        detail: String?,
        exchange: ServerWebExchange,
        extensions: Map<String, Any?> = emptyMap(),
    ): ResponseEntity<ProblemDetail> {
        val traceId = TraceIds.resolve(exchange)
        val problem =
            ProblemDetail.forStatus(status).apply {
                type = URI.create(TYPE_BASE + code.lowercase().replace('_', '-'))
                title = titleOf(code)
                this.detail = detail
                instance = URI.create(exchange.request.path.value())
                setProperty("code", code)
                setProperty("traceId", traceId)
                extensions.forEach { (name, value) -> setProperty(name, value) }
            }
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(TraceIds.HEADER, traceId)
            .body(problem)
    }

    // 오류 종류의 짧은 영어 이름. code에서 만든다(PRODUCT_NOT_DELETABLE → "Product not deletable").
    internal fun titleOf(code: String): String = code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// 응답의 traceId와 X-Trace-Id 헤더 값. 요청에 쓸 만한 X-Trace-Id가 있으면 그대로 쓰고, 없으면 새로 만든다.
// dozy-auth 스타터의 401·403과 같은 규칙(영문·숫자·하이픈 64자 이내)이다.
object TraceIds {
    const val HEADER = "X-Trace-Id"
    private val INCOMING = Regex("[A-Za-z0-9-]{1,64}")
    private val random = SecureRandom()

    fun resolve(exchange: ServerWebExchange): String =
        exchange.request.headers
            .getFirst(HEADER)
            ?.takeIf { INCOMING.matches(it) }
            ?: newId()

    private fun newId(): String = HexFormat.of().formatHex(ByteArray(16).also(random::nextBytes))
}
