package com.dozycoffee.catalog.domain.scheduledchange.exception

import com.dozycoffee.catalog.domain.shared.ErrorCode
import com.dozycoffee.catalog.domain.shared.ErrorType

enum class ScheduledChangeErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    INVALID_SCHEDULE_STATUS_TRANSITION(ErrorType.CONFLICT),
    NO_PENDING_SCHEDULE(ErrorType.NOT_FOUND),
    ;

    override val code: String get() = name
}
