package com.dozycoffee.catalog.schedule.infrastructure

import com.dozycoffee.catalog.common.exposed.DbNow
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeId
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import com.dozycoffee.catalog.schedule.domain.TargetKind
import com.dozycoffee.catalog.schedule.domain.exception.ScheduleAlreadyProcessedException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneOffset

@Repository
class ExposedScheduledChangeRepository : ScheduledChangeRepository {
    override suspend fun findById(id: ScheduledChangeId): ScheduledChange? =
        ScheduledChangesTable
            .selectAll()
            .where { ScheduledChangesTable.id eq id.value }
            .firstOrNull()
            ?.toScheduledChange()

    override suspend fun findPendingByTargetForUpdate(
        targetId: Long,
        targetKind: TargetKind,
        fieldName: String,
    ): ScheduledChange? =
        ScheduledChangesTable
            .selectAll()
            .where {
                (ScheduledChangesTable.targetId eq targetId) and
                    (ScheduledChangesTable.targetKind eq targetKind) and
                    (ScheduledChangesTable.fieldName eq fieldName) and
                    (ScheduledChangesTable.status eq ScheduleStatus.PENDING)
            }.forUpdate()
            .firstOrNull()
            ?.toScheduledChange()

    override suspend fun findAllPendingByTarget(
        targetId: Long,
        targetKind: TargetKind,
    ): List<ScheduledChange> =
        ScheduledChangesTable
            .selectAll()
            .where {
                (ScheduledChangesTable.targetId eq targetId) and
                    (ScheduledChangesTable.targetKind eq targetKind) and
                    (ScheduledChangesTable.status eq ScheduleStatus.PENDING)
            }.orderBy(ScheduledChangesTable.id)
            .map { it.toScheduledChange() }
            .toList()

    override suspend fun findDueForApplication(now: Instant): List<ScheduledChange> =
        ScheduledChangesTable
            .selectAll()
            .where {
                (ScheduledChangesTable.status eq ScheduleStatus.PENDING) and
                    (ScheduledChangesTable.effectiveAt lessEq now.atOffset(ZoneOffset.UTC))
            }.orderBy(ScheduledChangesTable.effectiveAt to SortOrder.ASC, ScheduledChangesTable.id to SortOrder.ASC)
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
            .map { it.toScheduledChange() }
            .toList()

    override suspend fun insert(newScheduledChange: ScheduledChange.NewScheduledChange): ScheduledChange {
        val id =
            ScheduledChangesTable.insert {
                it[targetId] = newScheduledChange.targetId
                it[targetKind] = newScheduledChange.targetKind
                it[fieldName] = newScheduledChange.fieldName
                it[newValue] = newScheduledChange.newValue
                it[effectiveDate] = newScheduledChange.effectiveDate
                it[effectiveAt] = newScheduledChange.effectiveAt.atOffset(ZoneOffset.UTC)
            }[ScheduledChangesTable.id]
        return ScheduledChange(
            id = ScheduledChangeId(id),
            targetId = newScheduledChange.targetId,
            newValue = newScheduledChange.newValue,
            effectiveDate = newScheduledChange.effectiveDate,
            effectiveAt = newScheduledChange.effectiveAt,
        )
    }

    // 예약에서 바뀌는 것은 상태뿐이다. 여전히 대기 중일 때만 바꾸고, 바뀐 행이 없으면 그 사이 다른 쪽
    // (관리자 취소, 배치 적용)이 먼저 처리한 것이므로 거부한다.
    override suspend fun save(scheduledChange: ScheduledChange): ScheduledChange {
        require(scheduledChange.status != ScheduleStatus.PENDING) {
            "대기 상태에서 바뀐 예약만 저장합니다: 예약 ${scheduledChange.id.value}"
        }
        val updated =
            ScheduledChangesTable.update({
                (ScheduledChangesTable.id eq scheduledChange.id.value) and
                    (ScheduledChangesTable.status eq ScheduleStatus.PENDING)
            }) {
                it[status] = scheduledChange.status
                it[updatedAt] = DbNow
            }
        if (updated == 0) {
            throw ScheduleAlreadyProcessedException(scheduledChange.id, scheduledChange.status)
        }
        return scheduledChange
    }

    private fun ResultRow.toScheduledChange(): ScheduledChange {
        val newValue = this[ScheduledChangesTable.newValue]
        check(
            newValue.targetKind == this[ScheduledChangesTable.targetKind] &&
                newValue.fieldName == this[ScheduledChangesTable.fieldName],
        ) { "예약 값과 대상·필드가 맞지 않습니다: 예약 ${this[ScheduledChangesTable.id]}" }
        return ScheduledChange(
            id = ScheduledChangeId(this[ScheduledChangesTable.id]),
            targetId = this[ScheduledChangesTable.targetId],
            newValue = newValue,
            effectiveDate = this[ScheduledChangesTable.effectiveDate],
            effectiveAt = this[ScheduledChangesTable.effectiveAt].toInstant(),
            status = this[ScheduledChangesTable.status],
        )
    }
}
