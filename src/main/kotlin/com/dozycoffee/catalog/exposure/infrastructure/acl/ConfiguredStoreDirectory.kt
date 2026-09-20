package com.dozycoffee.catalog.exposure.infrastructure.acl

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.exposure.application.port.StoreDirectoryPort
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

// Store BC 호출 방식이 정해지기 전까지 쓰는 임시 구현. 설정값(catalog.exposure.store-ids)에 적어 둔 매장만 돌려준다
// (docs/architecture/README.md 미정 사항의 "Store BC 호출 방식"). 비워 두면 판매 범위가 All인 상품의
// 판매 가능 매장 수가 0으로 나온다. 5단계에서 실제로 Store BC를 부르는 어댑터로 교체한다.
@Component
@EnableConfigurationProperties(CatalogExposureProperties::class)
class ConfiguredStoreDirectory(
    private val properties: CatalogExposureProperties,
) : StoreDirectoryPort {
    override suspend fun findAllIds(): Set<StoreId> = properties.storeIds.map(::StoreId).toSet()
}

@ConfigurationProperties(prefix = "catalog.exposure")
data class CatalogExposureProperties(
    val storeIds: List<Long> = emptyList(),
)
