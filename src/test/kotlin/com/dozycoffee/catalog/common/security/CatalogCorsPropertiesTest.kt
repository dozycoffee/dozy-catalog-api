package com.dozycoffee.catalog.common.security

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("CORS 허용 출처 설정")
class CatalogCorsPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = ["http://localhost:3000", "https://admin.dozycoffee.com", "https://admin.dozycoffee.test:8443"])
    fun `scheme host port 형식의 출처를 받는다`(origin: String) {
        assertEquals(listOf(origin), CatalogCorsProperties(listOf(origin)).allowedOrigins)
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://localhost:3001", "https://app.dozycoffee.com"])
    fun `공개 경로의 출처도 같은 형식으로 받는다`(origin: String) {
        assertEquals(listOf(origin), CatalogCorsProperties(publicAllowedOrigins = listOf(origin)).publicAllowedOrigins)
    }

    @ParameterizedTest
    @ValueSource(strings = ["*", "https://*.dozycoffee.com", "https://app.dozycoffee.com/"])
    fun `공개 경로의 출처가 와일드카드나 경로면 기동을 막는다`(origin: String) {
        assertFailsWith<IllegalArgumentException> { CatalogCorsProperties(publicAllowedOrigins = listOf(origin)) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["*", "https://*.dozycoffee.com", "http://localhost:3000/", "https://admin.dozycoffee.com/app", "localhost:3000", ""],
    )
    fun `와일드카드나 경로가 있거나 scheme이 없는 출처는 기동을 막는다`(origin: String) {
        assertFailsWith<IllegalArgumentException> { CatalogCorsProperties(listOf(origin)) }
    }
}
