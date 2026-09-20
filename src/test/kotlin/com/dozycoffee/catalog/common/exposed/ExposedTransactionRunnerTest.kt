package com.dozycoffee.catalog.common.exposed

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("ExposedTransactionRunner")
class ExposedTransactionRunnerTest : IntegrationTest() {
    @Autowired
    private lateinit var transactionRunner: TransactionRunner

    // 트랜잭션 동작만 확인하려고 V1의 tags 테이블을 최소 컬럼으로 매핑한다.
    private object TagsForTest : Table("tags") {
        val name = varchar("name", 100)
    }

    @Test
    fun `블록이 정상 종료되면 커밋된다`() =
        runTest {
            transactionRunner.inTransaction { TagsForTest.insert { it[name] = "신메뉴" } }

            assertEquals(1, countTags())
        }

    @Test
    fun `블록이 예외로 끝나면 블록 안의 변경이 모두 롤백된다`() =
        runTest {
            assertFailsWith<IllegalStateException> {
                transactionRunner.inTransaction {
                    TagsForTest.insert { it[name] = "신메뉴" }
                    TagsForTest.insert { it[name] = "시즌한정" }
                    error("중간 실패")
                }
            }

            assertEquals(0, countTags())
        }

    @Test
    fun `안쪽에서 다시 호출해도 바깥 트랜잭션과 함께 롤백된다`() =
        runTest {
            assertFailsWith<IllegalStateException> {
                transactionRunner.inTransaction {
                    transactionRunner.inTransaction { TagsForTest.insert { it[name] = "신메뉴" } }
                    error("바깥에서 실패")
                }
            }

            assertEquals(0, countTags())
        }

    private suspend fun countTags(): Long = transactionRunner.inTransaction { TagsForTest.selectAll().count() }
}
