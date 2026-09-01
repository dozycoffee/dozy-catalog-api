package com.dozycoffee.catalog.domain.scheduledchange

import com.dozycoffee.catalog.domain.scheduledchange.exception.InvalidScheduleStatusTransitionException
import com.dozycoffee.catalog.domain.scheduledchange.exception.NoPendingScheduleException
import com.dozycoffee.catalog.domain.shared.AggregateRoot
import java.time.LocalDate

// targetId/targetKind는 products.id 또는 option_groups.id를 다형 참조한다(FK 아님) —
// 모든 애그리거트 PK가 BIGINT라는 컨벤션에 기대어 Long으로 두고, ScheduledChange가
// Product/OptionGroup 도메인 타입을 몰라도 되는 범용 예약 메커니즘으로 남긴다.
// newValue도 같은 이유로 Any다 — 필드마다 실제 타입이 다르며(String, Money,
// StoreScope, 옵션 스냅샷 리스트 등), 값의 해석과 적용은 application 배치 서비스의 책임이다.
class ScheduledChange internal constructor(
    id: ScheduledChangeId,
    val targetId: Long,
    val targetKind: TargetKind,
    val fieldName: String,
    val newValue: Any,
    val effectiveDate: LocalDate,
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
}
