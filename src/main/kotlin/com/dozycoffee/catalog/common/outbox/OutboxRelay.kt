package com.dozycoffee.catalog.common.outbox

import com.dozycoffee.catalog.common.TransactionRunner
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

// 릴레이 한 번의 결과. skipped는 다른 인스턴스가 이미 릴레이 중이라 건너뛴 경우다.
// failed는 이번에 보내지 못한 이벤트 수, quarantined는 그중 한도를 넘겨 격리한 수다.
data class RelayResult(
    val published: Int = 0,
    val failed: Int = 0,
    val quarantined: Int = 0,
    val skipped: Boolean = false,
)

// 아웃박스의 대기 이벤트를 Kafka로 보낸다(docs/adr/0021).
// - 한 번에 한 인스턴스만 돈다. 트랜잭션 단위 권고 잠금을 잡지 못하면 건너뛴다.
// - id 순서로 하나씩 보내고 브로커가 받았다고 확인하면 전송 완료로 기록한다. 보낸 뒤 기록하기 전에 죽으면 다시 보내므로
//   at-least-once이고, 구독자는 eventId로 중복을 걸러낸다.
// - 한 건이 계속 실패해도 뒤의 이벤트를 막지 않도록, 실패가 한도(maxAttempts)에 이르면 격리한다.
//   다만 브로커 장애와 그 이벤트 자체의 문제(너무 큰 메시지 등)를 구분해야 한다. 장애 중에는 모든 이벤트가 실패하므로
//   이를 세면 멀쩡한 이벤트가 차례로 격리된다. 그래서 성공한 전송이 하나라도 있었을 때의 실패만 그 이벤트의 실패로 센다.
//   성공 없이 이어서 두 건이 실패하면 브로커 장애로 보고 횟수를 올리지 않은 채 이번 실행을 끝낸다.
// 전송을 기다리는 동안 DB 트랜잭션과 잠금을 잡고 있으므로 한 번에 읽는 건수를 작게 둔다.
@Component
class OutboxRelay(
    private val store: OutboxStore,
    private val publisher: OutboxPublisher,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val properties: CatalogOutboxProperties,
    private val metrics: OutboxMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Failure(
        val event: PendingOutboxEvent,
        val error: String,
    )

    suspend fun relayOnce(): RelayResult {
        val result =
            transactionRunner.inTransaction {
                if (!store.tryAcquireRelayLock()) return@inTransaction RelayResult(skipped = true)
                relayBatch(store.findPending(properties.relay.batchSize))
            }
        metrics.record(result)
        return result
    }

    private suspend fun relayBatch(events: List<PendingOutboxEvent>): RelayResult {
        var published = 0
        var failed = 0
        var quarantined = 0
        var sawSuccess = false
        // 성공 없이 처음 실패한 이벤트. 다음 이벤트의 결과를 보고 그 이벤트의 문제인지 장애인지 가린다.
        var suspect: Failure? = null

        suspend fun countFailure(failure: Failure) {
            val attempts = failure.event.attempts + 1
            failed++
            if (attempts >= properties.relay.maxAttempts) {
                store.quarantine(failure.event.id, clock.instant(), attempts, failure.error)
                quarantined++
                log.error(
                    "상품 이벤트를 {}번 보내지 못해 격리했습니다. eventId={}, 오류={}",
                    attempts,
                    failure.event.eventId,
                    failure.error,
                )
            } else {
                store.recordFailure(failure.event.id, attempts, failure.error)
                log.warn(
                    "상품 이벤트 전송 실패({}/{}). eventId={}, 오류={}",
                    attempts,
                    properties.relay.maxAttempts,
                    failure.event.eventId,
                    failure.error,
                )
            }
        }

        suspend fun recordOutage(failure: Failure) {
            store.recordFailure(failure.event.id, failure.event.attempts, failure.error)
            failed++
        }

        for (event in events) {
            val error = send(event)
            if (error == null) {
                store.markPublished(event.id, clock.instant())
                published++
                sawSuccess = true
                suspect?.let { countFailure(it) }
                suspect = null
                continue
            }
            val failure = Failure(event, error)
            when {
                sawSuccess -> countFailure(failure)
                suspect == null -> suspect = failure
                else -> {
                    log.warn("Kafka에 연달아 보내지 못해 이번 실행을 멈춥니다(브로커 장애로 봅니다). 오류={}", error)
                    recordOutage(suspect)
                    recordOutage(failure)
                    suspect = null
                    break
                }
            }
        }
        // 이벤트가 하나뿐이거나 마지막 하나가 실패한 경우다. 이 이벤트의 문제인지 가릴 수 없으므로 횟수를 올리지 않는다.
        suspect?.let { recordOutage(it) }
        return RelayResult(published, failed, quarantined)
    }

    // 성공이면 null, 실패면 오류 설명을 돌려준다. 취소는 삼키지 않는다.
    private suspend fun send(event: PendingOutboxEvent): String? =
        try {
            publisher.publish(event)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            (e.message ?: e.javaClass.simpleName).take(MAX_ERROR_LENGTH)
        }

    // 전송이 끝난 지 보관 기간이 지난 이벤트를 지운다. 한 번에 지우는 건수에 상한을 두고 배치마다 트랜잭션을 연다.
    suspend fun cleanup(): Int {
        val cleanup = properties.cleanup
        val threshold = clock.instant().minus(cleanup.retention)
        var total = 0
        do {
            val deleted = transactionRunner.inTransaction { store.deletePublishedBefore(threshold, cleanup.batchSize) }
            total += deleted
        } while (deleted >= cleanup.batchSize)
        return total
    }

    suspend fun refreshMetrics() {
        metrics.refresh(transactionRunner.inTransaction { store.stats() }, clock.instant())
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 1000
    }
}
