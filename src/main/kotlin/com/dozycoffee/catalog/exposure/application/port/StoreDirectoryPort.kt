package com.dozycoffee.catalog.exposure.application.port

import com.dozycoffee.catalog.core.StoreId

// 매장의 원본은 Store BC라, 판매 범위가 All인 상품의 "판매 가능 매장"은 Catalog 안에서 알 수 없다.
// 그 값을 외부 시스템 포트로 감싼다(docs/architecture/README.md 미정 사항의 "Store BC 호출 방식").
interface StoreDirectoryPort {
    // 전체 매장. 요약 조회의 판매 가능 매장 수는 이 크기이고, 상세 조회는 이 목록의 매장마다 노출 상태를 만든다.
    // 실제 연동에서 매장 수만 필요한 요약이 목록 전체를 받아 오는 게 부담이 되면, 수를 돌려주는 메서드를 따로 둔다.
    suspend fun findAllIds(): Set<StoreId>
}
