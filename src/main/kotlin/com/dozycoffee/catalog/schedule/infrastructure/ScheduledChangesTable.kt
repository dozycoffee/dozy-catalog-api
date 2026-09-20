package com.dozycoffee.catalog.schedule.infrastructure

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import com.dozycoffee.catalog.common.exposed.jsonb
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.TargetKind
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// target_id는 products.id 또는 option_groups.id를 다형 참조한다(FK 없음). 낙관적 잠금을 쓰지 않아 version이 없다.
object ScheduledChangesTable : Table("scheduled_changes") {
    val id = long("id").autoIncrement()
    val targetId = long("target_id")
    val targetKind = enumerationByName<TargetKind>("target_kind", 30)
    val fieldName = varchar("field_name", 64)
    val newValue = jsonb("new_value", ScheduledValueJsonbCodec)
    val effectiveDate = date("effective_date")
    val effectiveAt = timestampWithTimeZone("effective_at")
    val status = enumerationByName<ScheduleStatus>("status", 20).default(ScheduleStatus.PENDING)
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        check("scheduled_changes_target_kind_check") { targetKind inList TargetKind.entries }
        check("scheduled_changes_status_check") { status inList ScheduleStatus.entries }
        // 같은 대상·필드의 대기 예약은 최대 1건(요구사항 1.4)
        index("scheduled_changes_one_pending_per_field", true, targetId, targetKind, fieldName) {
            status eq ScheduleStatus.PENDING
        }
        // 배치 대상 조회: 대기 중이고 적용 시각이 지난 예약
        index("scheduled_changes_pending_due_idx", false, effectiveAt) { status eq ScheduleStatus.PENDING }
    }
}
