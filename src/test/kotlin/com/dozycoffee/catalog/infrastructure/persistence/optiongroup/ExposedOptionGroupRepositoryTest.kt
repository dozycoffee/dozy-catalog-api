package com.dozycoffee.catalog.infrastructure.persistence.optiongroup

import com.dozycoffee.catalog.application.shared.TransactionRunner
import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.domain.shared.VersionConflictException
import com.dozycoffee.catalog.fixture.option
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("ExposedOptionGroupRepository")
class ExposedOptionGroupRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: OptionGroupRepository

    @Nested
    @DisplayName("저장과 조회")
    inner class RoundTrip {
        @Test
        fun `등록한 옵션 그룹을 옵션 순서·키·가격·선택 방식·필수 여부 그대로 복원한다`() =
            runTest {
                val options = listOf(option("large", price = 1000), option("small", price = 0), option("medium", price = 500))
                val inserted =
                    insert(name = "사이즈", selectionType = SelectionType.MULTI, required = false, options = options)

                val found = assertNotNull(tx.inTransaction { repository.findById(inserted.id) })

                assertEquals("사이즈", found.name)
                assertEquals(SelectionType.MULTI, found.selectionType)
                assertEquals(false, found.required)
                assertEquals(options, found.options)
                assertEquals(0L, found.version)
            }

        @Test
        fun `등록하면 생성된 ID와 버전 0을 돌려준다`() =
            runTest {
                val inserted = insert(options = listOf(option("small")))

                assertEquals(1L, inserted.id.value)
                assertEquals(0L, inserted.version)
            }

        @Test
        fun `잠금 조회도 같은 옵션 그룹을 복원한다`() =
            runTest {
                val options = listOf(option("small"), option("large", price = 1000))
                val inserted = insert(options = options)

                val found = tx.inTransaction { repository.findByIdForUpdate(inserted.id) }

                assertEquals(options, assertNotNull(found).options)
            }
    }

    @Nested
    @DisplayName("옵션 목록 교체 저장")
    inner class ReplaceOptions {
        @Test
        fun `옵션 목록을 교체하고 저장하면 이전 옵션이 남지 않는다`() =
            runTest {
                val inserted = insert(options = listOf(option("small"), option("medium"), option("large")))
                val newOptions = listOf(option("large", price = 1500), option("venti", price = 2000))

                tx.inTransaction {
                    val group = assertNotNull(repository.findByIdForUpdate(inserted.id))
                    group.replaceOptions(newOptions)
                    repository.save(group)
                }

                assertEquals(newOptions, tx.inTransaction { repository.findById(inserted.id) }?.options)
                assertEquals(2L, count("SELECT count(*) FROM options WHERE option_group_id = ${inserted.id.value}"))
            }

        @Test
        fun `같은 그룹 안에서 옵션 키가 중복되면 DB가 거부한다`() =
            runTest {
                val inserted = insert(options = listOf(option("small")))

                assertFails {
                    execute(
                        "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                            "VALUES (${inserted.id.value}, 'small', '작은 사이즈', 0, 1)",
                    )
                }
                assertEquals(1L, count("SELECT count(*) FROM options WHERE option_group_id = ${inserted.id.value}"))
            }
    }

    @Nested
    @DisplayName("낙관적 잠금")
    inner class OptimisticLocking {
        @Test
        fun `저장에 성공하면 DB와 객체의 버전이 1 오른다`() =
            runTest {
                val inserted = insert(options = listOf(option("small")))

                val saved =
                    tx.inTransaction {
                        val group = assertNotNull(repository.findById(inserted.id))
                        group.rename("사이즈")
                        repository.save(group)
                    }

                assertEquals(1L, saved.version)
                assertEquals(1L, tx.inTransaction { repository.findById(inserted.id) }?.version)
            }

        @Test
        fun `오래된 버전으로 저장하면 충돌로 거부하고 DB 상태는 바뀌지 않는다`() =
            runTest {
                val inserted = insert(name = "사이즈", options = listOf(option("small"), option("large", price = 1000)))
                val stale = assertNotNull(tx.inTransaction { repository.findById(inserted.id) })
                val latestOptions = listOf(option("small"), option("medium", price = 500))
                tx.inTransaction {
                    val group = assertNotNull(repository.findById(inserted.id))
                    group.replaceOptions(latestOptions)
                    repository.save(group)
                }

                stale.rename("오래된 화면의 이름")
                stale.replaceOptions(listOf(option("venti", price = 2000)))
                val exception = assertFailsWith<VersionConflictException> { tx.inTransaction { repository.save(stale) } }

                assertEquals(0L, exception.expectedVersion)
                assertEquals(0L, stale.version)
                val current = assertNotNull(tx.inTransaction { repository.findById(inserted.id) })
                assertEquals("사이즈", current.name)
                assertEquals(latestOptions, current.options)
                assertEquals(1L, current.version)
            }
    }

    @Nested
    @DisplayName("삭제")
    inner class Deletion {
        @Test
        fun `연결한 상품이 없는 옵션 그룹을 삭제하면 옵션도 함께 삭제된다`() =
            runTest {
                val inserted = insert(options = listOf(option("small"), option("large")))

                tx.inTransaction { repository.delete(inserted.id) }

                assertNull(tx.inTransaction { repository.findById(inserted.id) })
                assertEquals(0L, count("SELECT count(*) FROM options WHERE option_group_id = ${inserted.id.value}"))
            }

        @Test
        fun `상품이 연결한 옵션 그룹은 DB가 삭제를 거부한다`() =
            runTest {
                val inserted = insert(options = listOf(option("small")))
                execute("INSERT INTO categories (name) VALUES ('음료')")
                execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
                execute(
                    "INSERT INTO products (name, category_id, base_price, tracks_inventory) " +
                        "VALUES ('아메리카노', 2, 4500, false)",
                )
                execute(
                    "INSERT INTO product_option_groups (product_id, option_group_id, display_order) " +
                        "VALUES (1, ${inserted.id.value}, 0)",
                )

                assertFails { tx.inTransaction { repository.delete(inserted.id) } }
                assertEquals(listOf(option("small")), tx.inTransaction { repository.findById(inserted.id) }?.options)
            }
    }

    private suspend fun insert(
        name: String = "옵션 그룹",
        selectionType: SelectionType = SelectionType.SINGLE,
        required: Boolean = true,
        options: List<Option>,
    ): OptionGroup =
        tx.inTransaction {
            repository.insert(OptionGroup.NewOptionGroup.of(name, selectionType, required, options))
        }
}
