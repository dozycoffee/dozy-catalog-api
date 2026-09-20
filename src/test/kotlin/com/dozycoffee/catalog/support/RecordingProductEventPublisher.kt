package com.dozycoffee.catalog.support

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.product.application.port.ProductEventPublisherPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 유스케이스가 어떤 이벤트를 발행했는지 확인하려고 쓰는 테스트 구현.
// 로그 구현(LoggingProductEventPublisher) 대신 주입된다.
class RecordingProductEventPublisher : ProductEventPublisherPort {
    private val events = mutableListOf<DomainEvent>()

    val published: List<DomainEvent> get() = events.toList()

    override suspend fun publish(events: List<DomainEvent>) {
        this.events += events
    }

    fun clear() = events.clear()
}

@TestConfiguration
class RecordingProductEventPublisherConfiguration {
    @Bean
    @Primary
    fun recordingProductEventPublisher() = RecordingProductEventPublisher()
}
