package com.dozycoffee.catalog.core

import com.dozycoffee.catalog.fixture.product
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("낙관적 잠금 버전 확인")
class VersionedAggregateRootTest {
    @Test
    fun `요청 버전이 현재 버전과 같으면 통과한다`() {
        val product = product(version = 3)

        product.checkVersion(3)
    }

    @Test
    fun `요청 버전이 현재 버전과 다르면 충돌로 거부한다`() {
        val product = product(version = 3)

        val exception = assertFailsWith<VersionConflictException> { product.checkVersion(2) }

        assertEquals(ErrorType.CONFLICT, exception.errorCode.type)
        assertEquals(2, exception.expectedVersion)
        assertEquals(3, exception.currentVersion)
        assertEquals(3, product.version)
    }
}
