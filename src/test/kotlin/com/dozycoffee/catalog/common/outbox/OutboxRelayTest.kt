package com.dozycoffee.catalog.common.outbox

import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 아웃박스 릴레이의 전송·재시도·격리 규칙(docs/adr/0021). Kafka는 외부 시스템 포트(OutboxPublisher)이므로 가짜로 바꾸고,
// 아웃박스 저장소와 DB는 실제로 쓴다. 실제 Kafka와의 왕복은 KafkaOutboxRelayTest가 확인한다.
@DisplayName("아웃박스 릴레이")
@Import(OutboxRelayTest.FakePublisherConfiguration::class)
class OutboxRelayTest : ApplicationTest() {
    class FakePublisher : OutboxPublisher {
        val sent = CopyOnWriteArrayList<UUID>()

        // 이 이벤트 ID들은 전송이 실패한다. brokerDown이면 모든 전송이 실패한다.
        val failing: MutableSet<UUID> = HashSet()
        var brokerDown = false

        override suspend fun publish(event: PendingOutboxEvent) {
            if (brokerDown || event.eventId in failing) throw OutboxPublishException("전송 실패 (eventId=${event.eventId})")
            sent += event.eventId
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FakePublisherConfiguration {
        @Bean
        @Primary
        fun fakePublisher(): FakePublisher = FakePublisher()
    }

    @Autowired
    private lateinit var relay: OutboxRelay

    @Autowired
    private lateinit var store: OutboxStore

    @Autowired
    private lateinit var publisher: FakePublisher

    @BeforeEach
    fun resetPublisher() {
        publisher.sent.clear()
        publisher.failing.clear()
        publisher.brokerDown = false
    }

    private suspend fun append(
        key: String = "1",
        createdAt: Instant = Instant.parse("2026-10-14T01:00:00Z"),
    ): UUID {
        val eventId = UuidV7.generate()
        tx {
            store.append(
                OutboxMessage(eventId, "dozy.catalog.product", key, """{"k":"$key"}""", null, createdAt),
            )
        }
        return eventId
    }

    private suspend fun publishedCount() = count("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL")

    private suspend fun attemptsOf(eventId: UUID) = count("SELECT attempts FROM outbox_events WHERE event_id = '$eventId'")

    private suspend fun quarantinedCount() = count("SELECT count(*) FROM outbox_events WHERE quarantined_at IS NOT NULL")

    @Nested
    @DisplayName("전송")
    inner class Sending {
        @Test
        fun `대기 이벤트를 기록한 순서대로 보내고 전송 완료로 기록한다`() =
            runTest {
                val ids = (1..3).map { append(key = "$it") }

                val result = relay.relayOnce()

                assertEquals(3, result.published)
                assertEquals(ids, publisher.sent.toList())
                assertEquals(3, publishedCount())
            }

        @Test
        fun `이미 보낸 이벤트는 다시 보내지 않는다`() =
            runTest {
                append()
                relay.relayOnce()
                publisher.sent.clear()

                val result = relay.relayOnce()

                assertEquals(0, result.published)
                assertTrue(publisher.sent.isEmpty())
            }

        @Test
        fun `한 번에 읽는 건수만큼만 보내고 나머지는 다음 실행에서 보낸다`() =
            runTest {
                val ids = (1..105).map { append(key = "$it") }

                assertEquals(100, relay.relayOnce().published)
                assertEquals(5, relay.relayOnce().published)
                assertEquals(ids, publisher.sent.toList())
            }

        @Test
        fun `다른 인스턴스가 릴레이 중이면 건너뛴다`() =
            runTest {
                append()
                val acquired = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val holder =
                    launch {
                        tx {
                            store.tryAcquireRelayLock()
                            acquired.complete(Unit)
                            release.await()
                        }
                    }
                acquired.await()

                val result = relay.relayOnce()

                assertTrue(result.skipped)
                assertTrue(publisher.sent.isEmpty())
                release.complete(Unit)
                holder.join()
            }
    }

    @Nested
    @DisplayName("이벤트 하나가 계속 실패할 때")
    inner class PoisonEvent {
        @Test
        fun `실패한 이벤트는 횟수를 올리고 뒤의 이벤트는 막지 않는다`() =
            runTest {
                val bad = append(key = "1")
                val good = append(key = "2")
                publisher.failing += bad

                val result = relay.relayOnce()

                assertEquals(1, result.published)
                assertEquals(1, result.failed)
                assertEquals(listOf(good), publisher.sent.toList())
                assertEquals(1, attemptsOf(bad))
                assertEquals(0, quarantinedCount())
            }

        @Test
        fun `한도에 이르면 격리하고 이후에는 읽지 않는다`() =
            runTest {
                val bad = append(key = "1")
                publisher.failing += bad

                repeat(5) {
                    append(key = "ok-$it")
                    relay.relayOnce()
                }

                assertEquals(1, quarantinedCount())
                assertEquals(5, attemptsOf(bad))
                assertEquals(
                    1,
                    count(
                        "SELECT count(*) FROM outbox_events WHERE event_id = '$bad' AND quarantined_at IS NOT NULL AND last_error IS NOT NULL",
                    ),
                )
                publisher.failing.clear()
                publisher.sent.clear()
                relay.relayOnce()
                assertTrue(publisher.sent.isEmpty())
            }

        @Test
        fun `실패한 이벤트는 다음 실행에서 다시 보낸다`() =
            runTest {
                val bad = append(key = "1")
                append(key = "2")
                publisher.failing += bad
                relay.relayOnce()
                publisher.failing.clear()

                relay.relayOnce()

                assertEquals(listOf(bad), publisher.sent.toList().filter { it == bad })
                assertEquals(2, publishedCount())
            }
    }

    @Nested
    @DisplayName("Kafka 장애")
    inner class BrokerOutage {
        @Test
        fun `성공 없이 연달아 실패하면 장애로 보고 횟수를 올리지 않는다`() =
            runTest {
                val ids = (1..3).map { append(key = "$it") }
                publisher.brokerDown = true

                repeat(10) { relay.relayOnce() }

                assertEquals(0, quarantinedCount())
                assertEquals(0, publishedCount())
                ids.forEach { assertEquals(0, attemptsOf(it)) }
                assertTrue(count("SELECT count(*) FROM outbox_events WHERE last_error IS NOT NULL") > 0)
            }

        @Test
        fun `장애가 풀리면 쌓인 이벤트를 순서대로 보낸다`() =
            runTest {
                val ids = (1..3).map { append(key = "$it") }
                publisher.brokerDown = true
                relay.relayOnce()
                publisher.brokerDown = false

                relay.relayOnce()

                assertEquals(ids, publisher.sent.toList())
            }

        @Test
        fun `이벤트가 하나뿐이면 문제를 가릴 수 없어 횟수를 올리지 않는다`() =
            runTest {
                val id = append()
                publisher.brokerDown = true

                repeat(7) { relay.relayOnce() }

                assertEquals(0, attemptsOf(id))
                assertEquals(0, quarantinedCount())
            }
    }

    @Nested
    @DisplayName("정리와 지표")
    inner class CleanupAndMetrics {
        @Test
        fun `보관 기간이 지난 전송 완료 이벤트만 지운다`() =
            runTest {
                val old = append(key = "old")
                val recent = append(key = "recent")
                val pending = append(key = "pending")
                execute("UPDATE outbox_events SET published_at = now() - interval '4 days' WHERE event_id = '$old'")
                execute("UPDATE outbox_events SET published_at = now() - interval '1 day' WHERE event_id = '$recent'")

                val deleted = relay.cleanup()

                assertEquals(1, deleted)
                assertEquals(0, count("SELECT count(*) FROM outbox_events WHERE event_id = '$old'"))
                assertEquals(1, count("SELECT count(*) FROM outbox_events WHERE event_id = '$recent'"))
                assertEquals(1, count("SELECT count(*) FROM outbox_events WHERE event_id = '$pending'"))
            }

        @Test
        fun `대기 건수와 격리 건수를 지표로 갱신한다`() =
            runTest {
                append(key = "1")
                append(key = "2")

                relay.refreshMetrics()

                val stats = tx { store.stats() }
                assertEquals(2, stats.pending)
                assertEquals(0, stats.quarantined)
            }
    }
}
