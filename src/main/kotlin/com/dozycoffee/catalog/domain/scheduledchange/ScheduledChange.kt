package com.dozycoffee.catalog.domain.scheduledchange

import com.dozycoffee.catalog.domain.scheduledchange.exception.InvalidEffectiveDateException
import com.dozycoffee.catalog.domain.scheduledchange.exception.InvalidScheduleStatusTransitionException
import com.dozycoffee.catalog.domain.scheduledchange.exception.NoPendingScheduleException
import com.dozycoffee.catalog.domain.shared.AggregateRoot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// targetId/targetKind는 products.id 또는 option_groups.id를 다형 참조한다(FK 아님) —
// 모든 애그리거트 PK가 BIGINT라는 컨벤션에 기대어 Long으로 두고, ScheduledChange가
// Product/OptionGroup 도메인 타입을 몰라도 되는 범용 예약 메커니즘으로 남긴다.
// newValue도 같은 이유로 Any다 — 필드마다 실제 타입이 다르며(String, Money,
// StoreScope, 옵션 스냅샷 리스트 등), 값의 해석과 적용은 application 배치 서비스의 책임이다.
//
// effectiveDate는 사용자가 지정한 업무 날짜이고, effectiveAt은 그 날 00시를 업무 시간대로 해석한 순간이다.
// 배치는 effectiveAt만 보고 대상을 고르므로 시간대를 몰라도 된다(docs/adr/0010).
class ScheduledChange internal constructor(
    id: ScheduledChangeId,
    val targetId: Long,
    val targetKind: TargetKind,
    val fieldName: String,
    val newValue: Any,
    val effectiveDate: LocalDate,
    val effectiveAt: Instant,
    status: ScheduleStatus = ScheduleStatus.PENDING,
) : AggregateRoot<ScheduledChangeId>(id) {
    var status: ScheduleStatus = status
        private set

    fun cancel() {
        if (status != ScheduleStatus.PENDING) {
            throw NoPendingScheduleException(targetId, targetKind, fieldName)
        }
        status = ScheduleStatus.CANCELLED
    }

    fun apply() {
        if (status != ScheduleStatus.PENDING) {
            throw InvalidScheduleStatusTransitionException(status, ScheduleStatus.APPLIED)
        }
        status = ScheduleStatus.APPLIED
    }

    // 00시에 적용을 시도했으나 실패한 경우. 대상 값은 그대로 유지되고
    // 실패 사실만 기록된다.
    fun fail() {
        if (status != ScheduleStatus.PENDING) {
            throw InvalidScheduleStatusTransitionException(status, ScheduleStatus.FAILED)
        }
        status = ScheduleStatus.FAILED
    }

    // 저장 전 예약. 적용일 검증과 적용 시각 계산을 거치지 않고는 만들 수 없다.
    class NewScheduledChange private constructor(
        val targetId: Long,
        val targetKind: TargetKind,
        val fieldName: String,
        val newValue: Any,
        val effectiveDate: LocalDate,
        val effectiveAt: Instant,
    ) {
        companion object {
            // today와 businessZone은 application이 Clock과 업무 시간대로 구해 넘긴다(도메인은 시계를 모른다).
            // 적용일은 내일 이후만 허용한다. 오늘 00시는 이미 지났으므로 오늘 적용은 즉시 반영으로 한다(요구사항 1.4).
            fun of(
                targetId: Long,
                targetKind: TargetKind,
                fieldName: String,
                newValue: Any,
                effectiveDate: LocalDate,
                today: LocalDate,
                businessZone: ZoneId,
            ): NewScheduledChange {
                if (!effectiveDate.isAfter(today)) {
                    throw InvalidEffectiveDateException(effectiveDate, today)
                }
                return NewScheduledChange(
                    targetId = targetId,
                    targetKind = targetKind,
                    fieldName = fieldName,
                    newValue = newValue,
                    effectiveDate = effectiveDate,
                    effectiveAt = effectiveDate.atStartOfDay(businessZone).toInstant(),
                )
            }
        }
    }
}
