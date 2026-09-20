package com.dozycoffee.catalog.support

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.exposure.application.port.StoreDirectoryPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// Store BC 대신 쓰는 테스트 구현. 전체 매장 목록을 테스트마다 정해 판매 가능 매장 수를 만든다.
// 설정값을 읽는 임시 구현(ConfiguredStoreDirectory) 대신 주입된다.
class FakeStoreDirectory : StoreDirectoryPort {
    private val storeIds = mutableSetOf<StoreId>()

    fun replaceAll(storeIds: List<StoreId>) {
        this.storeIds.clear()
        this.storeIds += storeIds
    }

    override suspend fun findAllIds(): Set<StoreId> = storeIds.toSet()
}

@TestConfiguration
class FakeStoreDirectoryConfiguration {
    @Bean
    @Primary
    fun fakeStoreDirectory() = FakeStoreDirectory()
}
