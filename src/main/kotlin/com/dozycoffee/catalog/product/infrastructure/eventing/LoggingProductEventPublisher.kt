package com.dozycoffee.catalog.product.infrastructure.eventing

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.product.application.port.ProductEventPublisherPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// 메시징 기술이 정해지기 전까지 쓰는 임시 구현. 발행 지점과 페이로드를 로그로만 남긴다.
@Component
class LoggingProductEventPublisher : ProductEventPublisherPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(events: List<DomainEvent>) {
        events.forEach { log.info("상품 이벤트 발행: {}", it) }
    }
}
