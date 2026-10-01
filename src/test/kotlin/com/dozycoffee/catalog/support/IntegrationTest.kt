package com.dozycoffee.catalog.support

import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer

// 영속성 통합 테스트의 공통 기반(docs/architecture/testing.md).
// - PostgreSQL 컨테이너는 JVM 전체에서 하나만 띄워 모든 통합 테스트가 공유한다.
//   @ServiceConnection이 R2DBC와 JDBC(Flyway) 연결 정보를 함께 만들어 주므로 설정 파일에 접속 정보를 두지 않는다.
// - 테스트마다 flyway_schema_history를 뺀 모든 테이블을 비운다. R2DBC는 테스트 트랜잭션 롤백이 어려워 TRUNCATE로 정리한다.
// integration 태그가 붙어 ./gradlew unitTest에서는 제외된다(Docker가 필요하므로).
@Tag("integration")
@SpringBootTest
abstract class IntegrationTest {
    @Autowired
    protected lateinit var databaseClient: DatabaseClient

    @BeforeEach
    fun cleanDatabase() =
        runBlocking {
            val tables =
                databaseClient
                    .sql(
                        """
                        SELECT tablename FROM pg_tables
                        WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                        """.trimIndent(),
                    ).map { row -> row.get("tablename", String::class.java)!! }
                    .all()
                    .collectList()
                    .awaitSingleOrNull()
                    .orEmpty()
            if (tables.isNotEmpty()) {
                databaseClient
                    .sql("TRUNCATE TABLE ${tables.joinToString { "\"$it\"" }} RESTART IDENTITY CASCADE")
                    .then()
                    .awaitSingleOrNull()
            }
        }

    // 테스트 데이터 준비·확인용 SQL. 검증 대상 Repository를 거치지 않고 DB에 직접 실행한다.
    protected suspend fun execute(sql: String) {
        databaseClient.sql(sql).then().awaitSingleOrNull()
    }

    protected suspend fun count(sql: String): Long =
        databaseClient
            .sql(sql)
            .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
            .one()
            .awaitSingleOrNull() ?: 0

    companion object {
        // 테스트 JVM에는 설정이 다른 Spring 컨텍스트가 여러 개 캐시되고, 컨텍스트마다 커넥션 풀을 가진다.
        // 기본 max_connections(100)로는 모자랄 수 있어 늘린다.
        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:18")
                .withCommand("postgres", "-c", "max_connections=300")
                .apply { start() }
    }
}
