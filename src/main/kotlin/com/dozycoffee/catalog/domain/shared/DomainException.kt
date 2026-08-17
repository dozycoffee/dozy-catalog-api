package com.dozycoffee.catalog.domain.shared

// 애그리거트별 구체적인 규칙 위반 예외(예: InvalidProductStatusTransitionException)가
// 이 클래스를 상속한다. presentation의 전역 예외 핸들러는 이 타입 하나만 캐치해서
// code/message로 응답을 만든다.
sealed class DomainException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
