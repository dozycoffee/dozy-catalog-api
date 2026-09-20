package com.dozycoffee.catalog.product.application.port

import com.dozycoffee.catalog.core.DomainEvent

// 상품 상태·정보 변경을 POS·정산 등 외부 서비스에 전파한다. 메시징 기술은 미정이라
// 지금은 로그 구현만 둔다(docs/architecture/README.md 미정 사항).
interface ProductEventPublisherPort {
    suspend fun publish(events: List<DomainEvent>)
}
