package com.dozycoffee.catalog.core

// 에러의 성격 분류. domain은 HTTP를 모르므로 상태 코드 대신 이 분류까지만 표현하고,
// 실제 HTTP 상태 코드로의 변환은 presentation에서 한 곳에 모아 처리한다.
enum class ErrorType {
    // 요청 값 자체의 형식/범위 위반 (현재 상태와 무관)
    INVALID_INPUT,

    // 대상이 존재하지 않음
    NOT_FOUND,

    // 현재 리소스 상태와 충돌 (상태가 바뀌면 성공할 수 있음)
    CONFLICT,

    // 값은 유효하지만 도메인 불변식 위반
    BUSINESS_RULE_VIOLATION,
}
