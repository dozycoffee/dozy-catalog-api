package com.dozycoffee.catalog.common.outbox

// 아웃박스 이벤트를 메시지 브로커로 내보내는 포트. 구현(Kafka)은 브로커가 메시지를 받았다고 확인한 뒤에야 돌아온다.
// 실패하면 예외를 던진다. 릴레이는 그 이벤트를 대기로 남겨 다시 시도한다.
interface OutboxPublisher {
    suspend fun publish(event: PendingOutboxEvent)
}

class OutboxPublishException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
