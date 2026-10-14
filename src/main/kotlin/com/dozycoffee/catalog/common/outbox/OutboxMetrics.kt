package com.dozycoffee.catalog.common.outbox

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

// 아웃박스 지표. 릴레이가 멈추면 이벤트가 조용히 쌓이므로 대기 건수와 가장 오래된 대기 이벤트의 나이로 알아챈다.
// 게이지는 DB를 직접 읽지 않고 refresh()가 주기적으로 채운 값을 읽는다(지표 수집이 DB 호출을 만들지 않도록).
@Component
class OutboxMetrics(
    registry: MeterRegistry,
) {
    private val pending = AtomicLong()
    private val quarantined = AtomicLong()
    private val oldestPendingAgeMillis = AtomicLong()

    private val publishedCounter = counter(registry, "published")
    private val failedCounter = counter(registry, "failed")
    private val quarantinedCounter = counter(registry, "quarantined")

    init {
        Gauge
            .builder("dozy.catalog.outbox.pending", pending) { it.get().toDouble() }
            .description("아직 Kafka로 보내지 않은 이벤트 수(격리 제외)")
            .register(registry)
        Gauge
            .builder("dozy.catalog.outbox.quarantined", quarantined) { it.get().toDouble() }
            .description("재시도 한도를 넘어 격리된 이벤트 수. 0보다 크면 운영자가 확인한다")
            .register(registry)
        Gauge
            .builder("dozy.catalog.outbox.oldest.pending.age", oldestPendingAgeMillis) { it.get() / 1000.0 }
            .baseUnit("seconds")
            .description("가장 오래 기다린 대기 이벤트의 나이. 릴레이가 멈췄는지 본다")
            .register(registry)
    }

    fun refresh(
        stats: OutboxStats,
        now: Instant,
    ) {
        pending.set(stats.pending)
        quarantined.set(stats.quarantined)
        oldestPendingAgeMillis.set(
            stats.oldestPendingCreatedAt?.let { Duration.between(it, now).toMillis().coerceAtLeast(0) } ?: 0,
        )
    }

    fun record(result: RelayResult) {
        publishedCounter.increment(result.published.toDouble())
        failedCounter.increment(result.failed.toDouble())
        quarantinedCounter.increment(result.quarantined.toDouble())
    }

    private fun counter(
        registry: MeterRegistry,
        result: String,
    ): Counter =
        Counter
            .builder("dozy.catalog.outbox.relay.events")
            .description("릴레이가 처리한 이벤트 수")
            .tag("result", result)
            .register(registry)
}
