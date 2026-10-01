package com.dozycoffee.catalog.schedule.presentation

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

// 예약 API의 오류 코드. 경로의 필드 이름은 API에만 있는 개념이라(도메인은 값 타입으로 필드를 정한다) presentation에 둔다.
// 클라이언트가 고칠 수 있는 요청 오류이므로 DomainException으로 던져 GlobalExceptionHandler가 다른 도메인 오류와
// 같은 Problem Details로 응답하게 한다. 공통 코드인 HTTP 수준의 NOT_FOUND(없는 경로)와 구분된다.
enum class ScheduleApiErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    UNKNOWN_SCHEDULE_FIELD(ErrorType.NOT_FOUND),
    ;

    override val code: String get() = name
}

// 예약할 수 없는 필드(docs/api/schedule.md 필드와 값). 재고 관리 여부처럼 예약 대상이 아닌 필드도 여기에 해당한다.
class UnknownScheduleFieldException(
    field: String,
) : DomainException(
        errorCode = ScheduleApiErrorCode.UNKNOWN_SCHEDULE_FIELD,
        message = "예약할 수 없는 필드입니다: $field",
    )
