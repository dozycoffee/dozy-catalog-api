package com.dozycoffee.catalog.domain.scheduledchange

import java.time.Instant

interface ScheduledChangeRepository {
    suspend fun findById(id: ScheduledChangeId): ScheduledChange?

    // register 시 기존 대기 예약 대체, cancel 시 대상 조회에 사용한다. 동일
    // 대상·동일 필드의 Pending은 항상 최대 1건이므로 단건으로 반환한다.
    suspend fun findPendingByTarget(
        targetId: Long,
        targetKind: TargetKind,
        fieldName: String,
    ): ScheduledChange?

    // 상품/옵션그룹 조회 화면에서 "필드별 현재 값 + 대기 중인 예약값·적용일"을
    // 함께 보여주기 위해, 대상 하나에 걸린 모든 필드의 Pending 예약을 한번에
    // 가져온다.
    suspend fun findAllPendingByTarget(
        targetId: Long,
        targetKind: TargetKind,
    ): List<ScheduledChange>

    // 배치 적용 대상 조회: 대기 중이고 적용 시각(effectiveAt)이 now 이전인 예약. 시간대와 무관하며,
    // 놓친 예약도 다음 실행에서 함께 고른다. 여러 워커의 중복 처리 방지는 SELECT ... FOR UPDATE SKIP LOCKED로 구현한다.
    suspend fun findDueForApplication(now: Instant): List<ScheduledChange>

    suspend fun insert(newScheduledChange: ScheduledChange.NewScheduledChange): ScheduledChange

    suspend fun save(scheduledChange: ScheduledChange): ScheduledChange
}
