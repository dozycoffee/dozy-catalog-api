package com.dozycoffee.catalog.common.event

import com.dozycoffee.catalog.core.DomainEvent

// 도메인 이벤트 구독자. 구독하는 모듈의 application에 두고 Spring 빈으로 등록하면 디스패처가 모아 쓴다.
// 관심 있는 이벤트 타입을 eventType으로 밝힌다 — Kotlin의 제네릭은 런타임에 지워져 구현 타입만으로는
// 어떤 이벤트를 받을지 알 수 없기 때문이다.
interface DomainEventHandler<E : DomainEvent> {
    val eventType: Class<E>

    // 발행 측의 트랜잭션 안에서 동기로 실행된다. 예외를 던지면 원래 변경까지 롤백된다.
    suspend fun handle(event: E)
}
