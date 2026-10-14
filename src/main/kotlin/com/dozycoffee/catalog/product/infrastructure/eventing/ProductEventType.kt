package com.dozycoffee.catalog.product.infrastructure.eventing

// 상품 이벤트의 eventType(docs/events/product.md 4절). {집계}_{과거형 동사} 대문자 스네이크다.
// 구독자는 종류와 관계없이 data의 상태로 복제본을 교체한다. 저장된 이벤트가 계속 읽혀야 하므로 이름은 바꾸지 않는다.
enum class ProductEventType(
    val wireName: String,
) {
    REGISTERED("PRODUCT_REGISTERED"),
    ACTIVATED("PRODUCT_ACTIVATED"),
    DISCONTINUED("PRODUCT_DISCONTINUED"),
    STORE_SCOPE_CHANGED("PRODUCT_STORE_SCOPE_CHANGED"),
    DELETED("PRODUCT_DELETED"),
}
