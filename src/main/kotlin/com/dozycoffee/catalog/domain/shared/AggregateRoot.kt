package com.dozycoffee.catalog.domain.shared

// 도메인 이벤트 수집(registerEvent/pullDomainEvents)은 실제로 이벤트를 발행하는
// 첫 애그리거트(Product) 구현 시점에 추가한다.
abstract class AggregateRoot<ID : Any>(
    id: ID,
) : Entity<ID>(id)
