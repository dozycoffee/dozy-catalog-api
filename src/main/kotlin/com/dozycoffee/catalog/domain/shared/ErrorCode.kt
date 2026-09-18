package com.dozycoffee.catalog.domain.shared

// 예외 종류를 식별하는 코드. 애그리거트별 enum(예: ProductErrorCode)이 구현하며,
// 각 코드는 응답 정책 결정에 쓰이는 ErrorType을 함께 가진다.
interface ErrorCode {
    val code: String
    val type: ErrorType
}
