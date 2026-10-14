package com.dozycoffee.catalog.common.outbox

import com.dozycoffee.catalog.common.exposed.AdvisoryLockNamespace
import com.dozycoffee.catalog.common.exposed.tryLockForTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class ExposedOutboxStore : OutboxStore {
    override suspend fun append(message: OutboxMessage) {
        OutboxEventsTable.insert {
            it[eventId] = message.eventId
            it[topic] = message.topic
            it[messageKey] = message.messageKey
            it[payload] = message.payload
            it[traceparent] = message.traceparent
            it[createdAt] = message.createdAt.atOffset()
        }
    }

    override suspend fun tryAcquireRelayLock(): Boolean = tryLockForTransaction(AdvisoryLockNamespace.OUTBOX_RELAY, 0)

    override suspend fun findPending(limit: Int): List<PendingOutboxEvent> =
        OutboxEventsTable
            .selectAll()
            .where { OutboxEventsTable.publishedAt.isNull() and OutboxEventsTable.quarantinedAt.isNull() }
            .orderBy(OutboxEventsTable.id to SortOrder.ASC)
            .limit(limit)
            .map {
                PendingOutboxEvent(
                    id = it[OutboxEventsTable.id],
                    eventId = it[OutboxEventsTable.eventId],
                    topic = it[OutboxEventsTable.topic],
                    messageKey = it[OutboxEventsTable.messageKey],
                    payload = it[OutboxEventsTable.payload],
                    attempts = it[OutboxEventsTable.attempts],
                )
            }.toList()

    override suspend fun markPublished(
        id: Long,
        at: Instant,
    ) {
        OutboxEventsTable.update({ OutboxEventsTable.id eq id }) {
            it[publishedAt] = at.atOffset()
        }
    }

    override suspend fun recordFailure(
        id: Long,
        attempts: Int,
        error: String,
    ) {
        OutboxEventsTable.update({ OutboxEventsTable.id eq id }) {
            it[OutboxEventsTable.attempts] = attempts
            it[lastError] = error
        }
    }

    override suspend fun quarantine(
        id: Long,
        at: Instant,
        attempts: Int,
        error: String,
    ) {
        OutboxEventsTable.update({ OutboxEventsTable.id eq id }) {
            it[OutboxEventsTable.attempts] = attempts
            it[lastError] = error
            it[quarantinedAt] = at.atOffset()
        }
    }

    // PostgreSQL의 DELETE는 LIMIT을 받지 않으므로 지울 id를 먼저 고른다.
    override suspend fun deletePublishedBefore(
        threshold: Instant,
        limit: Int,
    ): Int {
        val ids =
            OutboxEventsTable
                .select(OutboxEventsTable.id)
                .where { OutboxEventsTable.publishedAt.isNotNull() and (OutboxEventsTable.publishedAt less threshold.atOffset()) }
                .orderBy(OutboxEventsTable.publishedAt to SortOrder.ASC)
                .limit(limit)
                .map { it[OutboxEventsTable.id] }
                .toList()
        if (ids.isEmpty()) return 0
        return OutboxEventsTable.deleteWhere { OutboxEventsTable.id inList ids }
    }

    override suspend fun stats(): OutboxStats {
        val pendingFilter = OutboxEventsTable.publishedAt.isNull() and OutboxEventsTable.quarantinedAt.isNull()
        val pending = OutboxEventsTable.selectAll().where { pendingFilter }.count()
        val quarantined = OutboxEventsTable.selectAll().where { OutboxEventsTable.quarantinedAt.isNotNull() }.count()
        val oldest =
            OutboxEventsTable
                .select(OutboxEventsTable.createdAt)
                .where { pendingFilter }
                .orderBy(OutboxEventsTable.id to SortOrder.ASC)
                .limit(1)
                .firstOrNull()
                ?.get(OutboxEventsTable.createdAt)
                ?.toInstant()
        return OutboxStats(pending = pending, quarantined = quarantined, oldestPendingCreatedAt = oldest)
    }

    private fun Instant.atOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
}
