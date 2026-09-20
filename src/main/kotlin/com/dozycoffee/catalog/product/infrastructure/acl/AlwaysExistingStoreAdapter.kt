package com.dozycoffee.catalog.product.infrastructure.acl

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.port.ValidateStoreExistsPort
import org.springframework.stereotype.Component

// Store BC 호출 방식이 정해지기 전까지 쓰는 임시 구현. 모든 매장이 존재한다고 답한다
// (docs/architecture/README.md 미정 사항의 "Store BC 호출 방식").
// 5단계에서 실제로 Store BC를 부르는 StoreBcAdapter + StoreBcClient로 교체한다.
@Component
class AlwaysExistingStoreAdapter : ValidateStoreExistsPort {
    override suspend fun findMissing(storeIds: Set<StoreId>): Set<StoreId> = emptySet()
}
