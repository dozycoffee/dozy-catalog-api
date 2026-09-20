package com.dozycoffee.catalog.common.event

import com.dozycoffee.catalog.core.DomainEvent

// BC 안의 구독자에게 도메인 이벤트를 전달하는 유일한 수단. 애그리거트를 저장한 application 서비스가
// pullDomainEvents()로 꺼낸 이벤트를 넘기면, 구독 모듈이 등록한 DomainEventHandler가 같은 트랜잭션에서
// 동기로 실행된다. 핸들러가 실패하면 원래 변경도 함께 롤백된다(#65).
//
// 발행 측이 구독 측을 모르므로 모듈 간 의존 방향(store → product)이 유지된다. product은 자기 이벤트만
// 발행하고, 매장 설정 정리는 store가 핸들러를 등록해 처리한다(ADR-0015).
// 외부 서비스(POS·정산 등)로 나가는 전파는 이 경로가 아니라 ProductEventPublisherPort가 맡는다.
interface DomainEventDispatcher {
    suspend fun dispatch(events: List<DomainEvent>)
}
