package com.dozycoffee.catalog.product.domain.tag

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("Tag")
class TagTest {
    @Test
    fun `이름 변경은 즉시 반영된다`() {
        val tag = Tag(TagId(1), "신메뉴")

        tag.rename("시즌한정")

        assertEquals("시즌한정", tag.name)
    }
}
