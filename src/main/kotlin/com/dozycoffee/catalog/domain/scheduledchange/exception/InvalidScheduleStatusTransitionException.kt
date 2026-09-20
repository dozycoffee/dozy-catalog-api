package com.dozycoffee.catalog.domain.scheduledchange.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.domain.scheduledchange.ScheduleStatus

class InvalidScheduleStatusTransitionException(
    from: ScheduleStatus,
    to: ScheduleStatus,
) : DomainException(
        errorCode = ScheduledChangeErrorCode.INVALID_SCHEDULE_STATUS_TRANSITION,
        message = "예약을 $from 에서 $to 로 전환할 수 없습니다",
    )
