package com.dozycoffee.catalog.fixture

import com.dozycoffee.catalog.domain.scheduledchange.ScheduleStatus
import com.dozycoffee.catalog.domain.scheduledchange.ScheduledChange
import com.dozycoffee.catalog.domain.scheduledchange.ScheduledChangeId
import com.dozycoffee.catalog.domain.scheduledchange.TargetKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun scheduledChange(
    id: Long = 1,
    targetId: Long = 100,
    targetKind: TargetKind = TargetKind.PRODUCT,
    fieldName: String = "basePrice",
    newValue: Any = 5000L,
    effectiveDate: LocalDate = LocalDate.of(2026, 10, 1),
    effectiveAt: Instant = effectiveDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
    status: ScheduleStatus = ScheduleStatus.PENDING,
) = ScheduledChange(
    id = ScheduledChangeId(id),
    targetId = targetId,
    targetKind = targetKind,
    fieldName = fieldName,
    newValue = newValue,
    effectiveDate = effectiveDate,
    effectiveAt = effectiveAt,
    status = status,
)
