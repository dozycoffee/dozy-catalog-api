package com.dozycoffee.catalog.schedule.domain

import java.time.Instant

// 예약은 낙관적 잠금을 쓰지 않는다. 관리자의 등록·취소와 배치의 적용·실패가 겹치는 것은
// 행 잠금(FOR UPDATE, SKIP LOCKED)과 상태 조건 UPDATE로 막는다(docs/erd.md 동시성 처리).
interface ScheduledChangeRepository {
    suspend fun findById(id: ScheduledChangeId): ScheduledChange?

    // 같은 필드에 새 예약을 등록할 때 기존 대기 예약을 잠그고 취소·대체하는 데, 취소 요청에서 대상을 찾는 데 쓴다.
    // 동일 대상·동일 필드의 대기 예약은 항상 최대 1건이므로 단건으로 반환한다. 잠금은 트랜잭션이 끝날 때 풀린다.
    suspend fun findPendingByTargetForUpdate(
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

    // 배치 적용 대상 조회: 대기 중이고 적용 시각(effectiveAt)이 now 이전인 예약을 적용 시각 순으로 잠가 가져온다.
    // 시간대와 무관하며, 놓친 예약도 다음 실행에서 함께 고른다. 다른 트랜잭션이 잠근 예약은 건너뛰므로
    // (FOR UPDATE SKIP LOCKED) 여러 워커가 같은 예약을 두 번 처리하지 않는다.
    // limit은 한 번에 처리할 건수 상한이다. 남은 예약은 다음 실행에서 고른다.
    suspend fun findDueForApplication(
        now: Instant,
        limit: Int,
    ): List<ScheduledChange>

    // 배치가 예약 한 건을 적용하기 직전에 그 행만 다시 잠근다. 대상 조회와 적용을 각각 다른 트랜잭션에서 하므로
    // (건별 격리) 그 사이 다른 워커가 가져갔거나 관리자가 취소했을 수 있다. 그런 예약은 더는 대기가 아니거나
    // 다른 트랜잭션이 잠갔으므로 null이 돌아오고, 배치는 기다리지 않고 건너뛴다.
    suspend fun findPendingByIdForUpdateSkipLocked(id: ScheduledChangeId): ScheduledChange?

    // effectiveDate와 effectiveAt을 함께 저장한다. 같은 대상·필드에 대기 예약이 이미 있으면 DB의 부분 UNIQUE가 거부한다.
    suspend fun insert(newScheduledChange: ScheduledChange.NewScheduledChange): ScheduledChange

    // 대기 상태에서 바뀐 상태(적용완료·취소·실패)를 저장한다. 저장은 여전히 대기 중일 때만 일어나며,
    // 그 사이 다른 쪽이 먼저 처리했으면 ScheduleAlreadyProcessedException을 던진다.
    suspend fun save(scheduledChange: ScheduledChange): ScheduledChange
}
