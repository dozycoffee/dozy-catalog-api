package com.dozycoffee.catalog.product.infrastructure.eventing

import com.dozycoffee.catalog.common.BusinessTimeZone
import com.dozycoffee.catalog.common.outbox.OutboxMessage
import com.dozycoffee.catalog.common.outbox.OutboxStore
import com.dozycoffee.catalog.common.outbox.UuidV7
import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.product.application.port.ProductEventPublisherPort
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.event.ProductActivated
import com.dozycoffee.catalog.product.domain.product.event.ProductDiscontinued
import com.dozycoffee.catalog.product.domain.product.event.ProductStoreScopeChanged
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

// 상품 이벤트를 아웃박스에 기록한다(ADR-0021). 호출한 유스케이스의 트랜잭션 안에서 실행되므로
// 상품 변경과 이벤트가 함께 저장되거나 함께 사라진다.
// traceparent는 추적을 도입하기 전(#118)이라 보내지 않는다. 필드가 선택이라 계약 위반이 아니다.
@Component
class OutboxProductEventPublisher(
    private val outbox: OutboxStore,
    private val clock: Clock,
    private val businessTimeZone: BusinessTimeZone,
) : ProductEventPublisherPort {
    override suspend fun registered(product: Product) {
        record(product, ProductEventType.REGISTERED)
    }

    override suspend fun changed(
        product: Product,
        events: List<DomainEvent>,
    ) {
        val type = events.mapNotNull(::typeOf).lastOrNull() ?: return
        record(product, type)
    }

    override suspend fun deleted(product: Product) {
        val now = clock.instant()
        val eventId = UuidV7.generate(now.toEpochMilli())
        outbox.append(
            OutboxMessage(
                eventId = eventId,
                topic = ProductEventJson.TOPIC,
                messageKey = ProductEventJson.messageKey(product),
                payload = ProductEventJson.deleted(eventId, occurredAt(now), traceparent = null, product = product),
                traceparent = null,
                createdAt = now,
            ),
        )
    }

    private suspend fun record(
        product: Product,
        type: ProductEventType,
    ) {
        val now = clock.instant()
        val eventId = UuidV7.generate(now.toEpochMilli())
        outbox.append(
            OutboxMessage(
                eventId = eventId,
                topic = ProductEventJson.TOPIC,
                messageKey = ProductEventJson.messageKey(product),
                payload = ProductEventJson.state(eventId, type, occurredAt(now), traceparent = null, product = product),
                traceparent = null,
                createdAt = now,
            ),
        )
    }

    // 알릴 필요가 없는 도메인 이벤트는 null이다(이 서비스 안에서만 쓰이는 것).
    private fun typeOf(event: DomainEvent): ProductEventType? =
        when (event) {
            is ProductActivated -> ProductEventType.ACTIVATED
            is ProductDiscontinued -> ProductEventType.DISCONTINUED
            is ProductStoreScopeChanged -> ProductEventType.STORE_SCOPE_CHANGED
            else -> null
        }

    private fun occurredAt(now: java.time.Instant): OffsetDateTime = now.atZone(businessTimeZone.zoneId).toOffsetDateTime()
}
