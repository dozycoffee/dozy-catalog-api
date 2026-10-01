package com.dozycoffee.catalog.common.exposed

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Spring Boot가 spring.r2dbc.*(또는 docker-compose·Testcontainers 연결 정보)로 만든 ConnectionFactory를
// Exposed가 그대로 감싸 쓴다. 스키마는 Flyway가 만들므로 Exposed로 테이블을 만들지 않는다(docs/adr/0009).
@Configuration(proxyBeanMethods = false)
class ExposedConfiguration {
    @Bean
    fun r2dbcDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase = connectExposedDatabase(connectionFactory)
}

fun connectExposedDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
    R2dbcDatabase
        .connect(
            connectionFactory = connectionFactory,
            databaseConfig =
                R2dbcDatabaseConfig {
                    explicitDialect = PostgreSQLDialect()
                    // Exposed는 기본으로 DB 예외(R2dbcException)가 나면 트랜잭션 블록 전체를 최대 3번 다시 실행한다.
                    // 교착 상태만이 아니라 제약 위반·연결 오류에도 그렇다. 재시도는 오류를 가리고(동시성 버그가 테스트에 드러나지 않음),
                    // 블록 안의 외부 호출을 중복시킬 수 있으므로 끈다. 다시 시도할지는 호출하는 쪽이 정한다(docs/architecture/persistence.md).
                    defaultMaxAttempts = 1
                },
        ).also { it.loadConnectionMetadata() }

// Exposed는 식별자 규칙(identifierManager)·DB 버전 같은 연결 메타데이터를 처음 쓸 때 runBlocking으로 DB에 물어 읽는다.
// 첫 사용이 트랜잭션 연결의 Netty 이벤트 루프 스레드에서 일어나면, 그 스레드가 자기가 받아야 할 응답을 기다리며 영원히 멈춘다.
// 트랜잭션과 연결이 반환되지 않고 잡은 잠금도 풀리지 않는다. 그래서 시작할 때 이벤트 루프 밖에서 미리 읽어 둔다.
// 메타데이터는 트랜잭션의 연결로 읽으므로 짧은 트랜잭션을 연다.
private fun R2dbcDatabase.loadConnectionMetadata() =
    runBlocking {
        suspendTransaction(this@loadConnectionMetadata) {
            vendor
            version
            fullVersion
            identifierManager
        }
    }
