package com.dozycoffee.catalog.domain.scheduledchange.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class ScheduledChangeErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    INVALID_SCHEDULE_STATUS_TRANSITION(ErrorType.CONFLICT),
    NO_PENDING_SCHEDULE(ErrorType.NOT_FOUND),
    INVALID_EFFECTIVE_DATE(ErrorType.INVALID_INPUT),
    SCHEDULE_ALREADY_PROCESSED(ErrorType.CONFLICT),
    ;

    override val code: String get() = name
}
