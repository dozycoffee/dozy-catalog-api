package com.dozycoffee.catalog.fixture

import com.dozycoffee.catalog.domain.scheduledchange.ScheduleStatus
import com.dozycoffee.catalog.domain.scheduledchange.ScheduledChange
import com.dozycoffee.catalog.domain.scheduledchange.ScheduledChangeId
import com.dozycoffee.catalog.domain.scheduledchange.ScheduledValue
import com.dozycoffee.catalog.domain.scheduledchange.TargetKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// domain 테스트용 예약 값. 실제 필드별 값 타입은 application에 있고(docs/adr/0014), domain은 대상 종류와 필드 이름만 본다.
data class TestScheduledValue(
    override val targetKind: TargetKind = TargetKind.PRODUCT,
    override val fieldName: String = "basePrice",
    val value: Any = 5000L,
) : ScheduledValue

fun scheduledChange(
    id: Long = 1,
    targetId: Long = 100,
    newValue: ScheduledValue = TestScheduledValue(),
    effectiveDate: LocalDate = LocalDate.of(2026, 10, 1),
    effectiveAt: Instant = effectiveDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
    status: ScheduleStatus = ScheduleStatus.PENDING,
) = ScheduledChange(
    id = ScheduledChangeId(id),
    targetId = targetId,
    newValue = newValue,
    effectiveDate = effectiveDate,
    effectiveAt = effectiveAt,
    status = status,
)
