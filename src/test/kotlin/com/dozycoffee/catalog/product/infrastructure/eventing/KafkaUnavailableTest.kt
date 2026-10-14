package com.dozycoffee.catalog.product.infrastructure.eventing

import com.dozycoffee.catalog.common.outbox.OutboxMessage
import com.dozycoffee.catalog.common.outbox.OutboxStore
import com.dozycoffee.catalog.common.outbox.UuidV7
import com.dozycoffee.catalog.support.ApplicationTest
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Kafka가 없어도 앱이 뜨고 이벤트가 아웃박스에 남는지 확인한다(docs/adr/0021).
// 닿지 않는 주소를 가리켜 장애를 흉내 낸다. 릴레이가 켜진 컨텍스트는 끝나면 닫는다.
@DisplayName("Kafka에 닿지 못할 때")
@TestPropertySource(
    properties = [
        "catalog.outbox.relay.enabled=true",
        "catalog.outbox.relay.fixed-delay=PT0.1S",
        "catalog.outbox.relay.send-timeout=PT1S",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.producer.properties.max.block.ms=300",
        "spring.kafka.producer.properties.request.timeout.ms=300",
        "spring.kafka.producer.properties.delivery.timeout.ms=500",
    ],
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaUnavailableTest : ApplicationTest() {
    @Autowired
    private lateinit var store: OutboxStore

    @Autowired
    private lateinit var meters: MeterRegistry

    @Test
    fun `이벤트는 아웃박스에 남고 격리되지 않으며 지표로 쌓인 양을 알 수 있다`() =
        runTest {
            val now = Instant.now()
            tx {
                repeat(3) {
                    store.append(OutboxMessage(UuidV7.generate(), "dozy.catalog.product", "$it", """{"k":$it}""", null, now))
                }
            }

            // 릴레이가 여러 번 시도할 시간을 준다. runTest의 가상 시간이 아니라 실제 시간을 기다린다.
            withContext(Dispatchers.Default) { delay(3000) }

            assertEquals(3, count("SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND quarantined_at IS NULL"))
            assertEquals(0, count("SELECT count(*) FROM outbox_events WHERE attempts > 0"))
            assertTrue(count("SELECT count(*) FROM outbox_events WHERE last_error IS NOT NULL") > 0)
            assertNotNull(meters.find("dozy.catalog.outbox.pending").gauge())
        }
}
