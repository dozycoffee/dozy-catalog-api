package com.dozycoffee.catalog.domain.scheduledchange.exception

import com.dozycoffee.catalog.core.DomainException
import java.time.LocalDate

class InvalidEffectiveDateException(
    effectiveDate: LocalDate,
    today: LocalDate,
) : DomainException(
        errorCode = ScheduledChangeErrorCode.INVALID_EFFECTIVE_DATE,
        message = "예약 적용일은 내일 이후여야 합니다: 적용일 $effectiveDate, 오늘 $today",
    )
