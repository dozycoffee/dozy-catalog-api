package com.dozycoffee.catalog.common.exposed

import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("스키마 마이그레이션")
class SchemaMigrationTest : IntegrationTest() {
    @Autowired
    private lateinit var flyway: Flyway

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `앱이 시작되면 Flyway가 모든 마이그레이션을 적용한다`() {
        val info = flyway.info()

        assertTrue(info.pending().isEmpty())
        assertTrue(info.applied().isNotEmpty())
        assertTrue(info.applied().all { it.state == MigrationState.SUCCESS })
    }

    @Test
    fun `V1 스키마의 테이블이 모두 만들어진다`() =
        runTest {
            val tables =
                databaseClient
                    .sql("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                    .map { row -> row.get("tablename", String::class.java)!! }
                    .all()
                    .collectList()
                    .awaitSingle()
                    .toSet()

            assertEquals(
                setOf(
                    "flyway_schema_history",
                    "categories",
                    "tags",
                    "product_groups",
                    "option_groups",
                    "options",
                    "products",
                    "product_target_stores",
                    "product_tags",
                    "product_groups_map",
                    "product_option_groups",
                    "product_option_overrides",
                    "scheduled_changes",
                    "store_display_settings",
                    "store_product_availabilities",
                ),
                tables,
            )
        }

    @Test
    fun `JDBC DataSource 빈은 만들지 않는다`() {
        // Flyway는 시작 시 한 번만 JDBC를 쓴다. 요청 경로에서 블로킹 JDBC를 쓰지 않도록 연결 풀을 남기지 않는다.
        assertTrue(context.getBeanNamesForType(DataSource::class.java).isEmpty())
    }
}
