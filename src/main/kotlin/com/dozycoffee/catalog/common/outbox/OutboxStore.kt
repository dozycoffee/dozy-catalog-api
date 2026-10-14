package com.dozycoffee.catalog.common.outbox

import java.time.Instant
import java.util.UUID

// 아웃박스에 쓸 이벤트 하나. payload는 Kafka 값으로 나가는 JSON 그대로다.
data class OutboxMessage(
    val eventId: UUID,
    val topic: String,
    val messageKey: String,
    val payload: String,
    val traceparent: String?,
    val createdAt: Instant,
)

// 릴레이가 읽는 아직 안 보낸 이벤트. attempts는 지금까지 실패한 횟수다.
data class PendingOutboxEvent(
    val id: Long,
    val eventId: UUID,
    val topic: String,
    val messageKey: String,
    val payload: String,
    val attempts: Int,
)

// 지표용 요약. oldestPendingCreatedAt은 전송 대기 중인 가장 오래된 이벤트의 기록 시각이다(없으면 null).
data class OutboxStats(
    val pending: Long,
    val quarantined: Long,
    val oldestPendingCreatedAt: Instant?,
)

// 트랜잭셔널 아웃박스의 저장소(docs/adr/0021). 모든 메서드는 호출한 쪽의 트랜잭션 안에서 실행된다.
// append는 업무 변경과 같은 트랜잭션에서 부르고, 나머지는 릴레이가 부른다.
interface OutboxStore {
    suspend fun append(message: OutboxMessage)

    // 릴레이의 단일 실행을 위한 잠금. 잡으면 현재 트랜잭션이 끝날 때까지 다른 인스턴스의 릴레이는 건너뛴다.
    suspend fun tryAcquireRelayLock(): Boolean

    // 아직 안 보냈고 격리되지 않은 이벤트를 id 순서로 읽는다.
    suspend fun findPending(limit: Int): List<PendingOutboxEvent>

    suspend fun markPublished(
        id: Long,
        at: Instant,
    )

    // 실패 횟수와 마지막 오류를 기록한다. 이벤트는 계속 대기 상태다.
    suspend fun recordFailure(
        id: Long,
        attempts: Int,
        error: String,
    )

    // 재시도 한도를 넘은 이벤트를 격리한다. 격리된 이벤트는 릴레이가 읽지 않고 운영자가 되살린다.
    suspend fun quarantine(
        id: Long,
        at: Instant,
        attempts: Int,
        error: String,
    )

    // 전송이 끝난 지 오래된 이벤트를 지운다. 한 번에 지우는 건수에 상한을 둔다. 지운 건수를 돌려준다.
    suspend fun deletePublishedBefore(
        threshold: Instant,
        limit: Int,
    ): Int

    suspend fun stats(): OutboxStats
}
