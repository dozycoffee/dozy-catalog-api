package com.dozycoffee.catalog.common.paging

// 목록 조회의 ids는 최대 100개다(docs/api/README.md 목록 조회). 넘는 요청은 presentation이 요청 오류로 먼저 거르므로
// 여기까지 오면 호출 코드의 잘못이다(docs/architecture/exception.md).
const val MAX_IDS = 100

fun requireIdsWithinLimit(ids: Collection<*>?) {
    require(ids == null || ids.size <= MAX_IDS) { "ids는 최대 ${MAX_IDS}개입니다: ${ids?.size}" }
}
