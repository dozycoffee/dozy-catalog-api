package com.dozycoffee.catalog.support

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.port.StoreAssignabilityPort
import com.dozycoffee.catalog.product.application.port.UnassignableReason
import com.dozycoffee.catalog.product.domain.product.exception.StoreServiceUnavailableException
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// Store 서비스 대신 쓰는 테스트 구현(외부 시스템 포트). 없는 매장·폐점한 매장·서비스 장애를 만들어 거부 흐름을 확인하고,
// 유스케이스가 실제로 물어봤는지(또는 묻지 않았는지) 확인한다. 모든 매장을 지정할 수 있다고 답하는 로컬용 구현
// (AlwaysAssignableStoreAdapter) 대신 주입된다. StoreBcAdapter 자체는 StoreBcAdapterTest가 확인한다.
class FakeStoreAssignabilityChecker : StoreAssignabilityPort {
    private val unassignableStores = mutableMapOf<StoreId, UnassignableReason>()
    private val requests = mutableListOf<Set<StoreId>>()
    private var unavailable = false

    // 유스케이스가 매장을 확인한 요청들. 확인 없이 지나갔는지도 이 목록으로 본다.
    val checked: List<Set<StoreId>> get() = requests.toList()

    fun markMissing(storeId: StoreId) {
        unassignableStores[storeId] = UnassignableReason.NOT_FOUND
    }

    fun markClosed(storeId: StoreId) {
        unassignableStores[storeId] = UnassignableReason.CLOSED
    }

    // Store 서비스에 닿지 못하는 상황
    fun markUnavailable() {
        unavailable = true
    }

    fun reset() {
        unassignableStores.clear()
        requests.clear()
        unavailable = false
    }

    override suspend fun findUnassignable(storeIds: Set<StoreId>): Map<StoreId, UnassignableReason> {
        requests += storeIds
        if (unavailable) throw StoreServiceUnavailableException()
        return unassignableStores.filterKeys { it in storeIds }
    }
}

@TestConfiguration
class FakeStoreAssignabilityCheckerConfiguration {
    @Bean
    @Primary
    fun fakeStoreAssignabilityChecker() = FakeStoreAssignabilityChecker()
}
