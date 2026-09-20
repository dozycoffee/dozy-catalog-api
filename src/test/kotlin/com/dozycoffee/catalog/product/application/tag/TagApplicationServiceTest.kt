package com.dozycoffee.catalog.product.application.tag

import com.dozycoffee.catalog.product.application.tag.command.RenameTagCommand
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import com.dozycoffee.catalog.product.domain.tag.exception.TagNotFoundException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
