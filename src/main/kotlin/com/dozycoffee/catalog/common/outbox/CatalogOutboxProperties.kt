package com.dozycoffee.catalog.common.outbox

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// 아웃박스 릴레이와 정리 설정(docs/adr/0021). 값의 의미와 기본값을 정한 이유는 application.yaml에 있다.
@ConfigurationProperties(prefix = "catalog.outbox")
data class CatalogOutboxProperties(
    val relay: Relay = Relay(),
    val cleanup: Cleanup = Cleanup(),
) {
    data class Relay(
        val enabled: Boolean = true,
        val fixedDelay: Duration = Duration.ofSeconds(1),
        val batchSize: Int = 100,
        val maxAttempts: Int = 5,
        val sendTimeout: Duration = Duration.ofSeconds(10),
    ) {
        init {
            require(batchSize > 0) { "catalog.outbox.relay.batch-size는 1 이상이어야 합니다: $batchSize" }
            require(maxAttempts > 0) { "catalog.outbox.relay.max-attempts는 1 이상이어야 합니다: $maxAttempts" }
            require(!sendTimeout.isNegative && !sendTimeout.isZero) { "catalog.outbox.relay.send-timeout은 0보다 커야 합니다" }
        }
    }

    data class Cleanup(
        val retention: Duration = Duration.ofDays(3),
        val fixedDelay: Duration = Duration.ofHours(1),
        val batchSize: Int = 1000,
    ) {
        init {
            require(batchSize > 0) { "catalog.outbox.cleanup.batch-size는 1 이상이어야 합니다: $batchSize" }
        }
    }
}
