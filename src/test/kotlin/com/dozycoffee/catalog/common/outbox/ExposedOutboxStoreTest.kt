package com.dozycoffee.catalog.common.outbox

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ExposedOutboxStore")
class ExposedOutboxStoreTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var store: OutboxStore

    private val base = Instant.parse("2026-10-14T01:00:00Z")

    @Nested
    @DisplayName("기록과 대기 조회")
    inner class AppendAndFind {
        @Test
        fun `기록한 이벤트를 id 순서로 읽고 값이 그대로다`() =
            runTest {
                val first = message(key = "1", payload = """{"a":1}""")
                val second =
                    message(key = "2", payload = """{"b":2}""", traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                tx.inTransaction {
                    store.append(first)
                    store.append(second)
                }

                val pending = tx.inTransaction { store.findPending(10) }

                assertEquals(listOf(first.eventId, second.eventId), pending.map { it.eventId })
                assertEquals(listOf("1", "2"), pending.map { it.messageKey })
                assertEquals(first.payload, pending[0].payload)
                assertEquals(0, pending[0].attempts)
            }

        @Test
        fun `한 번에 읽는 건수에는 상한이 있다`() =
            runTest {
                tx.inTransaction { repeat(5) { store.append(message(key = "$it")) } }

                assertEquals(3, tx.inTransaction { store.findPending(3) }.size)
            }

        @Test
        fun `같은 이벤트 ID는 두 번 기록할 수 없다`() =
            runTest {
                val event = message()
                tx.inTransaction { store.append(event) }

                assertFailsWith<Exception> { tx.inTransaction { store.append(event) } }
                assertEquals(1, count("SELECT count(*) FROM outbox_events"))
            }

        @Test
        fun `기록한 트랜잭션이 롤백되면 이벤트도 사라진다`() =
            runTest {
                assertFailsWith<IllegalStateException> {
                    tx.inTransaction {
                        store.append(message())
                        error("업무 변경이 실패했다")
                    }
                }

                assertEquals(0, count("SELECT count(*) FROM outbox_events"))
            }
    }

    @Nested
    @DisplayName("전송 결과 기록")
    inner class Results {
        @Test
        fun `전송한 이벤트는 대기 목록에서 빠진다`() =
            runTest {
                tx.inTransaction { store.append(message(key = "1")) }
                val id = tx.inTransaction { store.findPending(10).single().id }

                tx.inTransaction { store.markPublished(id, base) }

                assertTrue(tx.inTransaction { store.findPending(10) }.isEmpty())
            }

        @Test
        fun `실패를 기록하면 이벤트는 계속 대기하고 횟수와 오류가 남는다`() =
            runTest {
                tx.inTransaction { store.append(message()) }
                val id = tx.inTransaction { store.findPending(10).single().id }

                tx.inTransaction { store.recordFailure(id, attempts = 2, error = "브로커에 닿지 않는다") }

                val pending = tx.inTransaction { store.findPending(10) }.single()
                assertEquals(2, pending.attempts)
                assertEquals("브로커에 닿지 않는다", textOf("SELECT last_error FROM outbox_events WHERE id = $id"))
            }

        @Test
        fun `격리한 이벤트는 대기 목록에서 빠지고 뒤의 이벤트는 그대로 읽힌다`() =
            runTest {
                tx.inTransaction {
                    store.append(message(key = "1"))
                    store.append(message(key = "2"))
                }
                val ids = tx.inTransaction { store.findPending(10).map { it.id } }

                tx.inTransaction { store.quarantine(ids[0], base, attempts = 5, error = "계속 실패") }

                assertEquals(listOf("2"), tx.inTransaction { store.findPending(10) }.map { it.messageKey })
            }

        @Test
        fun `전송 완료와 격리를 함께 기록할 수는 없다`() =
            runTest {
                tx.inTransaction { store.append(message()) }
                val id = tx.inTransaction { store.findPending(10).single().id }
                tx.inTransaction { store.markPublished(id, base) }

                assertFailsWith<Exception> { tx.inTransaction { store.quarantine(id, base, attempts = 5, error = "x") } }
            }
    }

    @Nested
    @DisplayName("정리와 통계")
    inner class CleanupAndStats {
        @Test
        fun `전송이 끝난 지 오래된 이벤트만 지운다`() =
            runTest {
                tx.inTransaction {
                    store.append(message(key = "old"))
                    store.append(message(key = "new"))
                    store.append(message(key = "pending"))
                }
                val ids = tx.inTransaction { store.findPending(10).map { it.id } }
                tx.inTransaction {
                    store.markPublished(ids[0], base)
                    store.markPublished(ids[1], base.plusSeconds(3600))
                }

                val deleted = tx.inTransaction { store.deletePublishedBefore(base.plusSeconds(1800), limit = 100) }

                assertEquals(1, deleted)
                assertEquals(listOf("new", "pending"), remainingKeys())
            }

        @Test
        fun `한 번에 지우는 건수에는 상한이 있다`() =
            runTest {
                tx.inTransaction { repeat(3) { store.append(message(key = "$it")) } }
                val ids = tx.inTransaction { store.findPending(10).map { it.id } }
                tx.inTransaction { ids.forEach { store.markPublished(it, base) } }

                assertEquals(2, tx.inTransaction { store.deletePublishedBefore(base.plusSeconds(1), limit = 2) })
                assertEquals(1, count("SELECT count(*) FROM outbox_events"))
            }

        @Test
        fun `대기와 격리 건수, 가장 오래된 대기 이벤트의 기록 시각을 돌려준다`() =
            runTest {
                tx.inTransaction {
                    store.append(message(key = "1", createdAt = base))
                    store.append(message(key = "2", createdAt = base.plusSeconds(10)))
                    store.append(message(key = "3", createdAt = base.plusSeconds(20)))
                }
                val ids = tx.inTransaction { store.findPending(10).map { it.id } }
                tx.inTransaction {
                    store.markPublished(ids[0], base)
                    store.quarantine(ids[2], base, attempts = 5, error = "x")
                }

                val stats = tx.inTransaction { store.stats() }

                assertEquals(1, stats.pending)
                assertEquals(1, stats.quarantined)
                assertEquals(base.plusSeconds(10), stats.oldestPendingCreatedAt)
            }

        @Test
        fun `대기 이벤트가 없으면 가장 오래된 기록 시각은 없다`() =
            runTest {
                val stats = tx.inTransaction { store.stats() }

                assertEquals(0, stats.pending)
                assertNull(stats.oldestPendingCreatedAt)
            }
    }

    @Nested
    @DisplayName("릴레이 단일 실행 잠금")
    inner class RelayLock {
        @Test
        fun `다른 트랜잭션이 잠금을 갖고 있으면 기다리지 않고 실패한다`() =
            runTest {
                val acquired = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val holder =
                    launch {
                        tx.inTransaction {
                            assertTrue(store.tryAcquireRelayLock())
                            acquired.complete(Unit)
                            release.await()
                        }
                    }
                acquired.await()

                val secondGotLock = tx.inTransaction { store.tryAcquireRelayLock() }

                assertFalse(secondGotLock)
                release.complete(Unit)
                holder.join()
            }

        @Test
        fun `잠금을 가진 트랜잭션이 끝나면 다시 잡을 수 있다`() =
            runTest {
                assertTrue(tx.inTransaction { store.tryAcquireRelayLock() })
                assertTrue(tx.inTransaction { store.tryAcquireRelayLock() })
            }
    }

    private fun message(
        key: String = "1",
        payload: String = """{"x":1}""",
        traceparent: String? = null,
        createdAt: Instant = base,
    ) = OutboxMessage(
        eventId = UUID.randomUUID(),
        topic = "dozy.catalog.product",
        messageKey = key,
        payload = payload,
        traceparent = traceparent,
        createdAt = createdAt,
    )

    private suspend fun remainingKeys(): List<String> =
        databaseClient
            .sql("SELECT message_key FROM outbox_events ORDER BY id")
            .map { row -> row.get("message_key", String::class.java)!! }
            .all()
            .collectList()
            .awaitSingle()

    private suspend fun textOf(sql: String): String =
        databaseClient
            .sql(sql)
            .map { row -> row.get(0, String::class.java)!! }
            .one()
            .awaitSingle()
}
