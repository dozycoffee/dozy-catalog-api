package com.dozycoffee.catalog.product.application.tag

import com.dozycoffee.catalog.product.application.tag.command.RenameTagCommand
import com.dozycoffee.catalog.product.application.tag.query.TagFilter
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
import org.junit.jupiter.api.BeforeEach
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

    @Nested
    @DisplayName("목록 조회")
    inner class Listing {
        // 등록 순과 이름 순이 다르도록 넣는다: 1 신메뉴, 2 Best, 3 베스트, 4 50%할인, 5 5000원할인, 6 new_menu, 7 newXmenu, 8 가을한정
        @BeforeEach
        fun setUpTags() =
            runTest {
                execute(
                    "INSERT INTO tags (name) VALUES " +
                        "('신메뉴'), ('Best'), ('베스트'), ('50%할인'), ('5000원할인'), ('new_menu'), ('newXmenu'), ('가을한정')",
                )
            }

        @Test
        fun `조건이 없으면 모든 태그를 돌려준다`() =
            runTest {
                assertEquals(8, names(TagFilter()).size)
            }

        @Test
        fun `등록 순이 아니라 한글은 가나다순으로 먼저, 영문은 대소문자를 가리지 않고 그 뒤에 돌려준다`() =
            runTest {
                execute("INSERT INTO tags (name) VALUES ('apple')")
                val filter = TagFilter(ids = setOf(TagId(1), TagId(2), TagId(3), TagId(8), TagId(9)))

                assertEquals(listOf("가을한정", "베스트", "신메뉴", "apple", "Best"), names(filter))
            }

        @Test
        fun `검색어는 대소문자를 가리지 않고 이름의 일부와 맞춘다`() =
            runTest {
                assertEquals(listOf("Best"), names(TagFilter(keyword = "bE")))
                assertEquals(listOf("베스트"), names(TagFilter(keyword = "스트")))
            }

        @Test
        fun `검색어의 퍼센트와 밑줄은 와일드카드가 아니라 글자로 찾는다`() =
            runTest {
                assertEquals(listOf("50%할인"), names(TagFilter(keyword = "50%")))
                assertEquals(listOf("new_menu"), names(TagFilter(keyword = "new_")))
            }

        @Test
        fun `공백뿐인 검색어는 거르지 않는다`() =
            runTest {
                assertEquals(8, names(TagFilter(keyword = "  ")).size)
            }

        @Test
        fun `ids를 주면 그 태그만 돌려주고 없는 ID는 빠진다`() =
            runTest {
                val filter = TagFilter(ids = setOf(TagId(1), TagId(3), TagId(999)))

                assertEquals(listOf(TagId(3), TagId(1)), service.list(filter).map { it.id })
            }

        @Test
        fun `빈 ids는 아무것도 돌려주지 않는다`() =
            runTest {
                assertEquals(emptyList(), names(TagFilter(ids = emptySet())))
            }

        @Test
        fun `ids와 검색어를 함께 주면 모두 만족하는 것만 남는다`() =
            runTest {
                val filter = TagFilter(ids = setOf(TagId(2), TagId(3)), keyword = "베스")

                assertEquals(listOf("베스트"), names(filter))
            }

        @Test
        fun `100개를 넘는 ids는 호출 코드 오류로 거부한다`() {
            assertFailsWith<IllegalArgumentException> {
                TagFilter(ids = (1L..101L).map(::TagId).toSet())
            }
        }

        private suspend fun names(filter: TagFilter) = service.list(filter).map { it.name }
    }

    private suspend fun createTag(name: String) = tx { tagRepository.findOrCreateByName(name) }

    private suspend fun insertProductWithTag(tagId: TagId) {
        execute("INSERT INTO categories (name) VALUES ('음료')")
        execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
        execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('아메리카노', 2, 4500, false)")
        execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, ${tagId.value})")
    }
}
