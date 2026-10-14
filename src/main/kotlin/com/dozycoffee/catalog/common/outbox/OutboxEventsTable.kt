package com.dozycoffee.catalog.common.outbox

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// 서비스 간 이벤트의 트랜잭셔널 아웃박스(docs/adr/0021). 도메인을 모르는 기술 공통이라 common에 둔다.
object OutboxEventsTable : Table("outbox_events") {
    val id = long("id").autoIncrement()
    val eventId = javaUUID("event_id").uniqueIndex("outbox_events_event_id_key")
    val topic = varchar("topic", 255)
    val messageKey = varchar("message_key", 255)
    val payload = text("payload")
    val traceparent = varchar("traceparent", 64).nullable()
    val createdAt = auditTimestamp("created_at")
    val publishedAt = timestampWithTimeZone("published_at").nullable()
    val attempts = integer("attempts").default(0)
    val quarantinedAt = timestampWithTimeZone("quarantined_at").nullable()
    val lastError = text("last_error").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        check("outbox_events_attempts_check") { attempts greaterEq 0 }
        check("outbox_events_published_or_quarantined") { publishedAt.isNull() or quarantinedAt.isNull() }
        // 릴레이 대상 조회: 아직 안 보냈고 격리되지 않은 행
        index("outbox_events_pending_idx", false, id) { publishedAt.isNull() and quarantinedAt.isNull() }
        // 정리 배치 대상 조회: 전송이 끝난 행
        index("outbox_events_published_idx", false, publishedAt) { publishedAt.isNotNull() }
    }
}
