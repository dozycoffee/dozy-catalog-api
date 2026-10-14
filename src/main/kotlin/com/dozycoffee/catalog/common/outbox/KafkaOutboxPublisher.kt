package com.dozycoffee.catalog.common.outbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

// Kafka로 보낸다(docs/adr/0021). 값은 아웃박스에 저장한 JSON 그대로이고 헤더는 쓰지 않는다(사내 MSA 이벤트 형식).
// Reactor Kafka와 리액티브 템플릿은 쓰지 않고 KafkaTemplate을 쓴다. 전송 결과는 코루틴에서 기다린다.
// 프로듀서 설정(acks=all, 멱등 전송, 타임아웃)은 application.yaml의 spring.kafka.producer에 있다.
@Component
@EnableConfigurationProperties(CatalogOutboxProperties::class)
class KafkaOutboxPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val properties: CatalogOutboxProperties,
) : OutboxPublisher {
    override suspend fun publish(event: PendingOutboxEvent) {
        val timeout = properties.relay.sendTimeout
        // send()는 처음 보낼 때 브로커의 메타데이터를 받느라 max.block.ms까지 호출 스레드를 막을 수 있다.
        // 이벤트 루프를 막지 않도록 블로킹 허용 스레드에서 호출한다.
        // 시간 초과는 withTimeout이 아니라 withTimeoutOrNull로 받는다. 전자가 던지는 예외는 취소로 취급되어 호출한 쪽이 삼킬 수 없다.
        val sent =
            withTimeoutOrNull(timeout.toMillis()) {
                val future =
                    withContext(Dispatchers.IO) {
                        kafkaTemplate.send(ProducerRecord(event.topic, event.messageKey, event.payload))
                    }
                future.await()
            }
        if (sent == null) {
            throw OutboxPublishException("Kafka 전송 확인이 ${timeout.toMillis()}ms 안에 오지 않았습니다 (eventId=${event.eventId})")
        }
    }
}
