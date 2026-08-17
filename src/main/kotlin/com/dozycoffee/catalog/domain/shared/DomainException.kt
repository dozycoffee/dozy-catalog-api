package com.dozycoffee.catalog.domain.shared

// 애그리거트별 구체적인 규칙 위반 예외(예: InvalidProductStatusTransitionException)가
// 이 클래스를 상속한다. presentation의 전역 예외 핸들러는 이 타입 하나만 캐치해서
// code/message로 응답을 만든다.
// sealed가 아니라 abstract인 이유: sealed는 서브클래스가 같은 패키지에 있어야 하는데,
// 예외는 domain/<aggregate>/exception/에 애그리거트별로 흩어져 있고 핸들러도
// 타입별 when 분기 없이 code/message만 읽으므로 sealed의 이점이 없다.
abstract class DomainException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
