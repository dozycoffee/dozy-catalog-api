package com.dozycoffee.catalog.product.infrastructure.acl

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.port.StoreAssignabilityPort
import com.dozycoffee.catalog.product.application.port.UnassignableReason
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

// Store 서비스 주소(catalog.store.base-url)가 없을 때 쓰는 로컬 개발·테스트용 구현. 모든 매장을 지정할 수 있다고 답한다.
// 운영(prod)은 주소가 없으면 기동하지 않으므로 이 구현이 쓰일 수 없다(application.yaml).
@Component
@ConditionalOnExpression("'\${catalog.store.base-url:}'.isBlank()")
class AlwaysAssignableStoreAdapter : StoreAssignabilityPort {
    override suspend fun findUnassignable(storeIds: Set<StoreId>): Map<StoreId, UnassignableReason> = emptyMap()
}
