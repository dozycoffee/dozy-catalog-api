package com.dozycoffee.catalog.infrastructure.persistence

import com.dozycoffee.catalog.application.shared.TransactionRunner
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.migration.r2dbc.MigrationUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

// 스키마의 주인은 Flyway이고 Exposed는 조회·변경만 한다(docs/adr/0009). 두 정의가 어긋나면 런타임에야 드러나므로,
// Exposed Table 정의에 맞추려면 실행해야 할 SQL이 하나도 없는지 검사한다.
@DisplayName("Exposed Table 정의와 Flyway 스키마의 일치")
class ExposedSchemaConsistencyTest : IntegrationTest() {
    @Autowired
    private lateinit var transactionRunner: TransactionRunner

    @Test
    fun `등록된 Exposed Table 정의는 Flyway 스키마와 일치한다`() =
        runTest {
            val statements = requiredStatements(ExposedTables.all)

            assertTrue(statements.isEmpty(), "Flyway 스키마와 다른 Exposed 정의가 있습니다:\n${statements.joinToString("\n")}")
        }

    @Test
    fun `스키마와 어긋난 Table 정의는 검사에서 드러난다`() =
        runTest {
            // 검사 장치 자체가 동작하는지 확인한다. V1의 tags 테이블에는 없는 컬럼을 가진 정의다.
            val mismatched =
                object : Table("tags") {
                    val name = varchar("name", 100)
                    val color = varchar("color", 20)
                }

            val statements = requiredStatements(listOf(mismatched))

            assertTrue(statements.any { it.contains("color") }, "불일치를 감지하지 못했습니다: $statements")
        }

    private suspend fun requiredStatements(tables: List<Table>): List<String> =
        if (tables.isEmpty()) {
            emptyList()
        } else {
            transactionRunner.inTransaction {
                MigrationUtils.statementsRequiredForDatabaseMigration(*tables.toTypedArray(), withLogs = false)
            }
        }
}
