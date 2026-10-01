package com.dozycoffee.catalog.common.web

import org.springframework.http.ResponseEntity

// 낙관적 잠금 버전을 HTTP 헤더로 주고받는다(docs/api/README.md 낙관적 잠금, ADR-0013).
// 응답은 ETag: "3", 요청은 If-Match: "3". 본문 없는 DELETE에도 같은 방식으로 받기 위해 본문 필드가 아니라 헤더를 쓴다.
object VersionHeaders {
    private val ENTITY_TAG = Regex("\"(\\d{1,18})\"")

    // 컨트롤러는 @RequestHeader(HttpHeaders.IF_MATCH, required = false)로 받은 값을 넘긴다.
    // 없으면 428, 형식이 틀리면(약한 태그 W/"3", *, 따옴표 없음 등) 400이다.
    fun requireIfMatch(ifMatch: String?): Long {
        if (ifMatch.isNullOrBlank()) throw VersionRequiredException()
        val match =
            ENTITY_TAG.matchEntire(ifMatch.trim())
                ?: throw InvalidRequestException("If-Match는 \"3\"처럼 따옴표로 감싼 버전 숫자여야 합니다: $ifMatch")
        return match.groupValues[1].toLong()
    }

    // 단건 응답. 본문의 version과 같은 값을 ETag에도 담는다.
    fun <T : Any> okWithVersion(
        body: T,
        version: Long,
    ): ResponseEntity<T> = ResponseEntity.ok().eTag("\"$version\"").body(body)
}
