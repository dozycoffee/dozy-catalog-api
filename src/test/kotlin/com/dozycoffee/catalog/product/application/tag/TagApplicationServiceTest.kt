package com.dozycoffee.catalog.product.application.tag

import com.dozycoffee.catalog.product.application.tag.command.RenameTagCommand
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import com.dozycoffee.catalog.product.domain.tag.exception.TagNameDuplicatedException
import com.dozycoffee.catalog.product.domain.tag.exception.TagNotFoundException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@DisplayName("태그 관리 (요구사항 1.7)")
class TagApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: TagApplicationService

    @Autowired
    private lateinit var tagRepository: TagRepository

    @Test
    fun `이름 변경은 즉시 반영된다`() =
        runTest {
            val tag = createTag("신메뉴")

            service.rename(RenameTagCommand(tag.id, "시즌한정"))

            assertEquals("시즌한정", tx { tagRepository.findById(tag.id) }?.name)
        }

    @Nested
    @DisplayName("이름 중복")
    inner class DuplicateName {
        @Test
        fun `다른 태그와 같은 이름으로 바꾸면 거부하고 두 태그의 이름은 그대로다`() =
            runTest {
                val newMenu = createTag("신메뉴")
                val best = createTag("베스트")

                assertFailsWith<TagNameDuplicatedException> { service.rename(RenameTagCommand(best.id, "신메뉴")) }

                assertEquals("신메뉴", tx { tagRepository.findById(newMenu.id) }?.name)
                assertEquals("베스트", tx { tagRepository.findById(best.id) }?.name)
            }

        @Test
        fun `자기 이름 그대로 바꾸는 요청은 허용한다`() =
            runTest {
                val tag = createTag("신메뉴")

                service.rename(RenameTagCommand(tag.id, "신메뉴"))

                assertEquals("신메뉴", tx { tagRepository.findById(tag.id) }?.name)
            }

        @Test
        fun `두 태그를 동시에 같은 이름으로 바꾸면 하나만 성공한다`() =
            runTest {
                val first = createTag("신메뉴")
                val second = createTag("베스트")

                val results =
                    coroutineScope {
                        listOf(first, second)
                            .map { tag ->
                                async(Dispatchers.IO) { runCatching { service.rename(RenameTagCommand(tag.id, "시즌한정")) } }
                            }.awaitAll()
                    }

                assertEquals(1, results.count { it.isSuccess })
                assertIs<TagNameDuplicatedException>(results.single { it.isFailure }.exceptionOrNull())
                assertEquals(1, count("SELECT count(*) FROM tags WHERE name = '시즌한정'"))
            }
    }

    @Test
    fun `태그를 삭제하면 참조하던 상품에서도 함께 제거된다`() =
        runTest {
            val tag = createTag("신메뉴")
            insertProductWithTag(tag.id)

            service.delete(tag.id)

            assertNull(tx { tagRepository.findById(tag.id) })
            assertEquals(0, count("SELECT count(*) FROM product_tags"))
            assertEquals(1, count("SELECT count(*) FROM products"))
        }

    @Test
    fun `없는 태그의 이름을 바꾸면 거부한다`() =
        runTest {
            assertFailsWith<TagNotFoundException> { service.rename(RenameTagCommand(TagId(999), "시즌한정")) }
        }

    @Test
    fun `없는 태그를 삭제하면 거부한다`() =
        runTest {
            assertFailsWith<TagNotFoundException> { service.delete(TagId(999)) }
        }

    private suspend fun createTag(name: String) = tx { tagRepository.findOrCreateByName(name) }

    private suspend fun insertProductWithTag(tagId: TagId) {
        execute("INSERT INTO categories (name) VALUES ('음료')")
        execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
        execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('아메리카노', 2, 4500, false)")
        execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, ${tagId.value})")
    }
}
