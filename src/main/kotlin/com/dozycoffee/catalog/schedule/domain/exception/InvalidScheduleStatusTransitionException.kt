package com.dozycoffee.catalog.schedule.domain.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus

class InvalidScheduleStatusTransitionException(
    from: ScheduleStatus,
    to: ScheduleStatus,
) : DomainException(
        errorCode = ScheduledChangeErrorCode.INVALID_SCHEDULE_STATUS_TRANSITION,
        message = "예약을 $from 에서 $to 로 전환할 수 없습니다",
    )
