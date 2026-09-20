package com.dozycoffee.catalog.support

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.port.ValidateStoreExistsPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// Store BC 대신 쓰는 테스트 구현. 없는 매장을 지정해 거부 흐름을 만들고, 유스케이스가 실제로
// 물어봤는지 확인한다. 모든 매장이 존재한다고 답하는 임시 구현(AlwaysExistingStoreAdapter) 대신 주입된다.
class FakeStoreExistenceValidator : ValidateStoreExistsPort {
    private val missingStoreIds = mutableSetOf<StoreId>()
    private val requests = mutableListOf<Set<StoreId>>()

    // 유스케이스가 존재를 확인한 요청들. 확인 없이 지나갔는지도 이 목록으로 본다.
    val checked: List<Set<StoreId>> get() = requests.toList()

    fun markMissing(storeId: StoreId) {
        missingStoreIds += storeId
    }

    fun reset() {
        missingStoreIds.clear()
        requests.clear()
    }

    override suspend fun findMissing(storeIds: Set<StoreId>): Set<StoreId> {
        requests += storeIds
        return storeIds intersect missingStoreIds
    }
}

@TestConfiguration
class FakeStoreExistenceValidatorConfiguration {
    @Bean
    @Primary
    fun fakeStoreExistenceValidator() = FakeStoreExistenceValidator()
}
