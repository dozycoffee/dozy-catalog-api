package com.dozycoffee.catalog.common.event

import com.dozycoffee.catalog.core.DomainEvent
import org.springframework.stereotype.Component

// Spring이 모아 준 핸들러 빈에 이벤트를 나눠 준다. 별도 스레드나 큐를 쓰지 않으므로 호출한 쪽의
// 트랜잭션과 코루틴에서 그대로 실행된다 — 정리가 끝나기 전의 중간 상태를 만들지 않기 위해서다(#65).
// 한 이벤트에 핸들러가 여럿이면 등록 순서대로 부르고, 하나라도 실패하면 그대로 올려 보내 전부 롤백되게 한다.
@Component
class RegisteredHandlerDomainEventDispatcher(
    private val handlers: List<DomainEventHandler<*>>,
) : DomainEventDispatcher {
    override suspend fun dispatch(events: List<DomainEvent>) {
        events.forEach { event ->
            handlers
                .filter { it.eventType.isInstance(event) }
                .forEach { handler ->
                    @Suppress("UNCHECKED_CAST")
                    (handler as DomainEventHandler<DomainEvent>).handle(event)
                }
        }
    }
}
