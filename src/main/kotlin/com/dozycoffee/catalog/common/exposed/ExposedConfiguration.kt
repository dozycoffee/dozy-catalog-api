package com.dozycoffee.catalog.common.exposed

import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Spring Boot가 spring.r2dbc.*(또는 docker-compose·Testcontainers 연결 정보)로 만든 ConnectionFactory를
// Exposed가 그대로 감싸 쓴다. 스키마는 Flyway가 만들므로 Exposed로 테이블을 만들지 않는다(docs/adr/0009).
@Configuration(proxyBeanMethods = false)
class ExposedConfiguration {
    @Bean
    fun r2dbcDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
        R2dbcDatabase.connect(
            connectionFactory = connectionFactory,
            databaseConfig =
                R2dbcDatabaseConfig {
                    explicitDialect = PostgreSQLDialect()
                    // Exposed는 기본으로 DB 예외(R2dbcException)가 나면 트랜잭션 블록 전체를 최대 3번 다시 실행한다.
                    // 교착 상태만이 아니라 제약 위반·연결 오류에도 그렇다. 재시도는 오류를 가리고(동시성 버그가 테스트에 드러나지 않음),
                    // 블록 안의 외부 호출을 중복시킬 수 있으므로 끈다. 다시 시도할지는 호출하는 쪽이 정한다(docs/architecture/persistence.md).
                    defaultMaxAttempts = 1
                },
        )
}
