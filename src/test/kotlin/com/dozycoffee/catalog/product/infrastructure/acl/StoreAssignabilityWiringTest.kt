package com.dozycoffee.catalog.product.infrastructure.acl

import com.dozycoffee.catalog.product.application.port.StoreAssignabilityPort
import com.dozycoffee.catalog.support.IntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertIs

// 주소 설정에 따라 어떤 구현이 쓰이는지 확인한다. 운영(prod)은 주소가 없으면 기동하지 않으므로(application.yaml)
// 검증 없는 구현이 운영에서 쓰이는 일은 없다. 클라이언트 설정(dozy.auth.client.*)이 있으면 system token 클라이언트가 만들어진다.
@DisplayName("Store 서비스 확인 구현의 선택")
class StoreAssignabilityWiringTest {
    @Nested
    @DisplayName("주소가 없으면")
    inner class WithoutBaseUrl : IntegrationTest() {
        @Autowired
        private lateinit var port: StoreAssignabilityPort

        @Test
        fun `모든 매장을 지정할 수 있다고 답하는 로컬용 구현을 쓴다`() {
            assertIs<AlwaysAssignableStoreAdapter>(port)
        }
    }

    @Nested
    @DisplayName("주소와 system client 설정이 있으면")
    @TestPropertySource(
        properties = [
            "catalog.store.base-url=http://localhost:1",
            "dozy.auth.client.enabled=true",
            "dozy.auth.client.client-id=svc-catalog",
            "dozy.auth.client.client-secret=test-secret",
        ],
    )
    inner class WithBaseUrl : IntegrationTest() {
        @Autowired
        private lateinit var port: StoreAssignabilityPort

        @Test
        fun `Store 서비스를 부르는 구현을 쓴다`() {
            assertIs<StoreBcAdapter>(port)
        }
    }
}
