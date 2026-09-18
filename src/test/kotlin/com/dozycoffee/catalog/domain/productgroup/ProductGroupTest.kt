package com.dozycoffee.catalog.domain.productgroup

import com.dozycoffee.catalog.domain.productgroup.event.ProductGroupDeleted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("ProductGroup")
class ProductGroupTest {
    @Test
    fun `이름 변경은 즉시 반영된다`() {
        val group = ProductGroup(ProductGroupId(1), "여름 시즌")

        group.rename("겨울 시즌")

        assertEquals("겨울 시즌", group.name)
    }

    @Test
    fun `삭제하면 ProductGroupDeleted 이벤트를 등록한다`() {
        val group = ProductGroup(ProductGroupId(3), "여름 시즌")

        group.delete()

        val events = group.pullDomainEvents()
        assertEquals(1, events.size)
        val event = assertIs<ProductGroupDeleted>(events.single())
        assertEquals(ProductGroupId(3), event.productGroupId)
    }

    @Test
    fun `삭제는 참조 여부와 무관하게 항상 허용된다`() {
        val group = ProductGroup(ProductGroupId(1), "여름 시즌")

        group.delete()

        assertEquals("여름 시즌", group.name)
    }

    @Test
    fun `등록된 이벤트는 한 번만 꺼내진다`() {
        val group = ProductGroup(ProductGroupId(1), "여름 시즌")
        group.delete()

        assertEquals(1, group.pullDomainEvents().size)
        assertTrue(group.pullDomainEvents().isEmpty())
    }
}
