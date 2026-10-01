package com.dozycoffee.catalog.common.exposed

import com.dozycoffee.catalog.support.IntegrationTest
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

@DisplayName("Exposed 데이터베이스 구성")
class ExposedConfigurationTest : IntegrationTest() {
    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    // 처음 쓸 때 runBlocking으로 DB에 묻는 메타데이터다. 이벤트 루프 스레드에서 처음 읽으면 그 스레드가 멈추므로
    // 만들 때 미리 읽어 둬야 한다. 컨텍스트의 빈은 다른 테스트가 이미 읽었을 수 있어 새로 만들어 확인한다.
    @ParameterizedTest
    @ValueSource(strings = ["vendor", "version", "fullVersion", "identifierManager"])
    fun `연결 메타데이터를 만들 때 미리 읽어 둔다`(property: String) {
        val database = connectExposedDatabase(connectionFactory)

        assertTrue(database.isLoaded(property), "$property 를 아직 읽지 않았다")
    }

    private fun R2dbcDatabase.isLoaded(property: String): Boolean {
        val delegate = R2dbcDatabase::class.java.getDeclaredField("$property\$delegate").apply { isAccessible = true }
        return (delegate.get(this) as Lazy<*>).isInitialized()
    }
}
