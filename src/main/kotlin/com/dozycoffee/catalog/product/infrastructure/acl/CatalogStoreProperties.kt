package com.dozycoffee.catalog.product.infrastructure.acl

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// Store 서비스 호출 설정. baseUrl이 없으면 로컬용 임시 구현을 쓴다(AlwaysAssignableStoreAdapter).
@ConfigurationProperties(prefix = "catalog.store")
data class CatalogStoreProperties(
    val baseUrl: String? = null,
    // 한 번 부를 때 기다리는 시간. 사람이 누른 요청 안에서 부르므로 짧게 둔다.
    val timeout: Duration = Duration.ofSeconds(3),
)
