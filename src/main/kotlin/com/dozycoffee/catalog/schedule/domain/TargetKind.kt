package com.dozycoffee.catalog.schedule.domain

// 예약 대상. 상품-옵션 그룹 연결의 예약(연결 목록, 옵션 그룹별 예외)도 상품을 대상으로 하고
// 필드 이름으로 구분한다(docs/adr/0014).
enum class TargetKind {
    PRODUCT,
    OPTION_GROUP,
}
