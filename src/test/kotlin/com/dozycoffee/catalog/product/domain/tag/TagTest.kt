package com.dozycoffee.catalog.product.domain.tag

import com.dozycoffee.catalog.product.domain.tag.event.TagDeleted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("Tag")
class TagTest {
    @Test
    fun `이름 변경은 즉시 반영된다`() {
        val tag = Tag(TagId(1), "신메뉴")

        tag.rename("시즌한정")

        assertEquals("시즌한정", tag.name)
    }

    @Test
    fun `삭제하면 TagDeleted 이벤트를 등록한다`() {
        val tag = Tag(TagId(7), "베스트")

        tag.delete()

        val events = tag.pullDomainEvents()
        assertEquals(1, events.size)
        val event = assertIs<TagDeleted>(events.single())
        assertEquals(TagId(7), event.tagId)
    }

    @Test
    fun `삭제는 참조 여부와 무관하게 항상 허용된다`() {
        // 참조하던 상품에서 태그를 제거하는 건 이벤트 구독 측의 책임이라
        // 애그리거트 자체는 가드하지 않는다.
        val tag = Tag(TagId(1), "신메뉴")

        tag.delete()

        assertEquals("신메뉴", tag.name)
    }

    @Test
    fun `등록된 이벤트는 한 번만 꺼내진다`() {
        val tag = Tag(TagId(1), "신메뉴")
        tag.delete()

        assertEquals(1, tag.pullDomainEvents().size)
        assertTrue(tag.pullDomainEvents().isEmpty())
    }
}
