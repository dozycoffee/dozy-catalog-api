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
            databaseConfig = R2dbcDatabaseConfig { explicitDialect = PostgreSQLDialect() },
        )
}
