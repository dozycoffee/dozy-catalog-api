package com.dozycoffee.catalog.product.application.port

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.product.domain.product.Product

// 다른 서비스가 복제해 쓰는 상품 상태를 이벤트로 알린다(docs/events/product.md, ADR-0021).
// 구현은 이벤트를 Kafka로 직접 보내지 않고 **호출한 트랜잭션 안에서** 아웃박스에 기록한다. 기록이 실패하면 상품 변경도 롤백되고,
// 상품 변경이 롤백되면 이벤트도 남지 않는다. 실제 전송은 릴레이가 나중에 한다.
//
// 이벤트는 바뀐 부분이 아니라 **저장된 상품의 상태 전체**를 담는다. 도메인 이벤트는 ID만 담으므로 상태는 저장 후의 Product에서 읽는다.
interface ProductEventPublisherPort {
    // 새 상품을 저장한 직후. 항상 DRAFT이고 순번은 0이다.
    suspend fun registered(product: Product)

    // 상품을 저장한 직후. events는 이번 변경에서 나온 도메인 이벤트(pullDomainEvents 결과)이며,
    // 다른 서비스가 알아야 하는 변경(활성화, 단종, 판매 범위 변경)이 없으면 아무것도 기록하지 않는다.
    // 여러 개면 마지막 변경의 종류로 하나만 기록한다. 담기는 상태는 저장 후의 최종 상태다.
    suspend fun changed(
        product: Product,
        events: List<DomainEvent>,
    )

    // 삭제하기 전에 불러 둔 상품을 넘긴다. 순번은 삭제 직전 버전에 1을 더한 값이다.
    suspend fun deleted(product: Product)
}
