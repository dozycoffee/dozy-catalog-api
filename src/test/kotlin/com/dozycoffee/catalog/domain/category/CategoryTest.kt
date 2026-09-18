package com.dozycoffee.catalog.domain.category

import com.dozycoffee.catalog.domain.category.exception.InvalidParentCategoryException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("Category")
class CategoryTest {
    @Test
    fun `이름 변경은 즉시 반영된다`() {
        val category = TopLevelCategory(CategoryId(1), "음료")

        category.rename("논커피")

        assertEquals("논커피", category.name)
    }

    @Nested
    @DisplayName("대분류를 소분류로 강등")
    inner class BecomeChildOf {
        @Test
        fun `자기 자신을 부모로 지정할 수 없다`() {
            val category = TopLevelCategory(CategoryId(1), "음료")

            assertFailsWith<InvalidParentCategoryException> {
                category.becomeChildOf(parent = category, hasChildren = false)
            }
        }

        @Test
        fun `이미 하위 카테고리가 있으면 다른 카테고리의 하위가 될 수 없다`() {
            // 2단계 계층을 넘어서는 구조가 만들어지는 것을 막는다.
            val category = TopLevelCategory(CategoryId(1), "커피")
            val parent = TopLevelCategory(CategoryId(2), "음료")

            assertFailsWith<InvalidParentCategoryException> {
                category.becomeChildOf(parent = parent, hasChildren = true)
            }
        }

        @Test
        fun `강등해도 식별자와 이름은 유지되고 부모만 지정된다`() {
            val category = TopLevelCategory(CategoryId(1), "커피")
            val parent = TopLevelCategory(CategoryId(2), "음료")

            val child = category.becomeChildOf(parent = parent, hasChildren = false)

            assertEquals(CategoryId(1), child.id)
            assertEquals("커피", child.name)
            assertEquals(CategoryId(2), child.parentId)
        }
    }

    @Nested
    @DisplayName("소분류 이동/승격")
    inner class ChildCategoryTransition {
        @Test
        fun `다른 대분류로 이동해도 식별자와 이름은 유지된다`() {
            // 소분류를 이동해도 이 소분류를 참조하는 상품에는 영향이 없어야 하므로
            // categoryId가 보존되는 것이 핵심이다.
            val child = ChildCategory(CategoryId(1), "커피", parentId = CategoryId(2))
            val newParent = TopLevelCategory(CategoryId(3), "시즌 음료")

            val moved = child.changeParent(newParent)

            assertEquals(CategoryId(1), moved.id)
            assertEquals("커피", moved.name)
            assertEquals(CategoryId(3), moved.parentId)
        }

        @Test
        fun `대분류로 승격하면 부모가 사라진다`() {
            val child = ChildCategory(CategoryId(1), "커피", parentId = CategoryId(2))

            val topLevel = child.becomeTopLevel()

            assertEquals(CategoryId(1), topLevel.id)
            assertEquals("커피", topLevel.name)
        }
    }
}
