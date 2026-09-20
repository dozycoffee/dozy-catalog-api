package com.dozycoffee.catalog.schedule.domain.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.schedule.domain.TargetKind

class NoPendingScheduleException(
    targetId: Long,
    targetKind: TargetKind,
    fieldName: String,
) : DomainException(
        errorCode = ScheduledChangeErrorCode.NO_PENDING_SCHEDULE,
        message = "대기 중인 예약이 없습니다: $targetKind($targetId)의 $fieldName 필드",
    )
