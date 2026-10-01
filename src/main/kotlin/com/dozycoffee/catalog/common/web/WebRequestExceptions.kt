package com.dozycoffee.catalog.common.web

// 도메인 규칙이 아니라 요청 자체가 API 계약에 맞지 않을 때의 오류(docs/api/README.md 오류 응답).
// 도메인 예외(DomainException)와 달리 presentation이 요청을 읽는 단계에서만 던진다.

// 400 INVALID_REQUEST. 필드·파라미터 형식이 틀렸거나 상한(ids·size)을 넘었다. message가 응답의 detail이 된다.
class InvalidRequestException(
    message: String,
) : RuntimeException(message)

// 428 VERSION_REQUIRED. 낙관적 잠금 버전이 필요한 요청에 If-Match가 없다(ADR-0013).
class VersionRequiredException : RuntimeException("이 요청에는 If-Match 헤더로 버전을 보내야 합니다")
