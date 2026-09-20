package com.dozycoffee.catalog.infrastructure.persistence.tag

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.domain.tag.TagRepository
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.insert
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

@DisplayName("ExposedTagRepository")
class ExposedTagRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: TagRepository

    @Nested
    @DisplayName("이름으로 등록")
    inner class FindOrCreate {
        @Test
        fun `없는 이름이면 새로 만들고 조회할 수 있다`() =
            runTest {
                val created = tx.inTransaction { repository.findOrCreateByName("신메뉴") }

                val found = tx.inTransaction { repository.findById(created.id) }
                assertEquals("신메뉴", found?.name)
            }

        @Test
        fun `같은 이름이면 기존 태그를 재사용한다`() =
            runTest {
                val first = tx.inTransaction { repository.findOrCreateByName("신메뉴") }

                val second = tx.inTransaction { repository.findOrCreateByName("신메뉴") }

                assertEquals(first.id, second.id)
                assertEquals(1, count("SELECT count(*) FROM tags"))
            }

        @Test
        fun `같은 이름을 동시에 등록해도 태그는 하나만 생긴다`() =
            runTest {
                val tags =
                    coroutineScope {
                        (1..10)
                            .map { async(Dispatchers.IO) { tx.inTransaction { repository.findOrCreateByName("신메뉴") } } }
                            .awaitAll()
                    }

                assertEquals(1, tags.map { it.id }.distinct().size)
                assertEquals(1, count("SELECT count(*) FROM tags"))
            }

        @Test
        fun `같은 이름의 태그를 직접 두 번 넣으면 DB가 거부한다`() =
            runTest {
                tx.inTransaction { TagsTable.insert { it[name] = "신메뉴" } }

                assertFails { tx.inTransaction { TagsTable.insert { it[name] = "신메뉴" } } }
            }
    }

    @Test
    fun `이름을 바꿔 저장하면 조회에 반영된다`() =
        runTest {
            val tag = tx.inTransaction { repository.findOrCreateByName("신메뉴") }
            tag.rename("시즌한정")

            tx.inTransaction { repository.save(tag) }

            assertEquals("시즌한정", tx.inTransaction { repository.findById(tag.id) }?.name)
        }

    @Test
    fun `태그를 삭제하면 상품과의 연결도 함께 삭제된다`() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('아메리카노', 2, 4500, false)")
            val tag = tx.inTransaction { repository.findOrCreateByName("신메뉴") }
            execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, ${tag.id.value})")

            tx.inTransaction { repository.delete(tag.id) }

            assertNull(tx.inTransaction { repository.findById(tag.id) })
            assertEquals(0, count("SELECT count(*) FROM product_tags"))
            assertEquals(1, count("SELECT count(*) FROM products"))
        }
}
