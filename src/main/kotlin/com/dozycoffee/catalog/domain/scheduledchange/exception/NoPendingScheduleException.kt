package com.dozycoffee.catalog.domain.scheduledchange.exception

import com.dozycoffee.catalog.domain.scheduledchange.TargetKind
import com.dozycoffee.catalog.domain.shared.DomainException

class NoPendingScheduleException(
    targetId: Long,
    targetKind: TargetKind,
    fieldName: String,
) : DomainException(
        errorCode = ScheduledChangeErrorCode.NO_PENDING_SCHEDULE,
        message = "대기 중인 예약이 없습니다: $targetKind($targetId)의 $fieldName 필드",
    )
