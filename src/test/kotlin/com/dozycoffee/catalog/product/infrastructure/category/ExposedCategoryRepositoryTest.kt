package com.dozycoffee.catalog.product.infrastructure.category

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.domain.category.CategoryRepository
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.TopLevelCategory
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ExposedCategoryRepository")
class ExposedCategoryRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: CategoryRepository

    @Nested
    @DisplayName("노출 순서")
    inner class DisplayOrder {
        @Test
        fun `새 카테고리는 같은 형제의 맨 뒤에 놓인다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val food = tx.inTransaction { repository.insertTopLevel("푸드") }
                val coffee = tx.inTransaction { repository.insertChild("커피", beverage) }
                val tea = tx.inTransaction { repository.insertChild("차", beverage) }
                val dessert = tx.inTransaction { repository.insertChild("디저트", food) }

                assertEquals(listOf(beverage.id, food.id), tx.inTransaction { repository.findAll(topLevelOnly = true) }.map { it.id })
                assertEquals(listOf(coffee.id, tea.id), tx.inTransaction { repository.findAll(parentId = beverage.id) }.map { it.id })
                assertEquals(listOf(dessert.id), tx.inTransaction { repository.findAll(parentId = food.id) }.map { it.id })
            }

        @Test
        fun `순서를 바꾸면 그 순서대로 조회된다`() =
            runTest {
                val a = tx.inTransaction { repository.insertTopLevel("가") }
                val b = tx.inTransaction { repository.insertTopLevel("나") }
                val c = tx.inTransaction { repository.insertTopLevel("다") }

                tx.inTransaction { repository.replaceOrder(listOf(c.id, a.id, b.id)) }

                assertEquals(listOf(c.id, a.id, b.id), tx.inTransaction { repository.findAll(topLevelOnly = true) }.map { it.id })
            }

        @Test
        fun `부모를 바꾸면 새 형제의 맨 뒤로 가고 이름만 바꾸면 순서를 유지한다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val food = tx.inTransaction { repository.insertTopLevel("푸드") }
                val coffee = tx.inTransaction { repository.insertChild("커피", beverage) }
                val tea = tx.inTransaction { repository.insertChild("차", beverage) }
                val dessert = tx.inTransaction { repository.insertChild("디저트", food) }

                coffee.rename("원두 커피")
                tx.inTransaction { repository.save(coffee) }
                assertEquals(listOf(coffee.id, tea.id), tx.inTransaction { repository.findAll(parentId = beverage.id) }.map { it.id })

                tx.inTransaction { repository.save(coffee.changeParent(food)) }

                assertEquals(listOf(dessert.id, coffee.id), tx.inTransaction { repository.findAll(parentId = food.id) }.map { it.id })
                assertEquals(listOf(tea.id), tx.inTransaction { repository.findAll(parentId = beverage.id) }.map { it.id })
            }

        @Test
        fun `승격하면 대분류의 맨 뒤로 간다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val coffee = tx.inTransaction { repository.insertChild("커피", beverage) }
                val food = tx.inTransaction { repository.insertTopLevel("푸드") }

                tx.inTransaction { repository.save(coffee.becomeTopLevel(hasProducts = false)) }

                assertEquals(
                    listOf(beverage.id, food.id, coffee.id),
                    tx.inTransaction { repository.findAll(topLevelOnly = true) }.map { it.id },
                )
            }

        @Test
        fun `값이 같으면 id 순이다`() =
            runTest {
                execute("INSERT INTO categories (name) VALUES ('가'), ('나')")

                assertEquals(listOf(1L, 2L), tx.inTransaction { repository.findAll() }.map { it.id.value })
            }
    }

    @Nested
    @DisplayName("저장과 조회")
    inner class RoundTrip {
        @Test
        fun `대분류와 소분류를 타입대로 복원한다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val coffee = tx.inTransaction { repository.insertChild("커피", beverage) }

                assertIs<TopLevelCategory>(tx.inTransaction { repository.findById(beverage.id) })
                val found = assertIs<ChildCategory>(tx.inTransaction { repository.findById(coffee.id) })
                assertEquals("커피", found.name)
                assertEquals(beverage.id, found.parentId)
            }

        @Test
        fun `대분류 조회는 소분류를 돌려주지 않는다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val coffee = tx.inTransaction { repository.insertChild("커피", beverage) }

                assertNull(tx.inTransaction { repository.findTopLevelById(coffee.id) })
                assertNull(tx.inTransaction { repository.findTopLevelByIdForUpdate(coffee.id) })
                assertEquals(beverage.id, tx.inTransaction { repository.findTopLevelByIdForUpdate(beverage.id) }?.id)
            }

        @Test
        fun `하위 카테고리가 있는지 확인한다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val food = tx.inTransaction { repository.insertTopLevel("푸드") }
                tx.inTransaction { repository.insertChild("커피", beverage) }

                assertTrue(tx.inTransaction { repository.hasChildren(beverage.id) })
                assertFalse(tx.inTransaction { repository.hasChildren(food.id) })
            }
    }

    @Nested
    @DisplayName("계층 변경 저장")
    inner class HierarchyChange {
        @Test
        fun `소분류를 대분류로 승격해 저장하면 대분류로 조회된다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val coffee = tx.inTransaction { repository.insertChild("커피", beverage) }

                tx.inTransaction { repository.save(coffee.becomeTopLevel(hasProducts = false)) }

                assertIs<TopLevelCategory>(tx.inTransaction { repository.findById(coffee.id) })
            }

        @Test
        fun `대분류를 다른 대분류 아래로 옮겨 저장하면 소분류로 조회된다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val tea = tx.inTransaction { repository.insertTopLevel("차") }

                tx.inTransaction { repository.save(tea.becomeChildOf(beverage, hasChildren = false)) }

                val found = assertIs<ChildCategory>(tx.inTransaction { repository.findById(tea.id) })
                assertEquals(beverage.id, found.parentId)
            }

        @Test
        fun `자기 자신을 부모로 두는 행은 DB가 거부한다`() =
            runTest {
                tx.inTransaction { repository.insertTopLevel("음료") }

                assertFails { execute("UPDATE categories SET parent_category_id = 1 WHERE id = 1") }
            }
    }

    @Nested
    @DisplayName("삭제")
    inner class Deletion {
        @Test
        fun `참조가 없는 카테고리는 삭제된다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }

                tx.inTransaction { repository.delete(beverage.id) }

                assertNull(tx.inTransaction { repository.findById(beverage.id) })
            }

        @Test
        fun `상품이 참조하는 소분류는 DB가 삭제를 거부한다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                val coffee = tx.inTransaction { repository.insertChild("커피", beverage) }
                execute(
                    "INSERT INTO products (name, category_id, base_price, tracks_inventory) " +
                        "VALUES ('아메리카노', ${coffee.id.value}, 4500, false)",
                )

                assertFails { tx.inTransaction { repository.delete(coffee.id) } }
                assertIs<ChildCategory>(tx.inTransaction { repository.findById(coffee.id) })
            }

        @Test
        fun `하위 카테고리가 있는 대분류는 DB가 삭제를 거부한다`() =
            runTest {
                val beverage = tx.inTransaction { repository.insertTopLevel("음료") }
                tx.inTransaction { repository.insertChild("커피", beverage) }

                assertFails { tx.inTransaction { repository.delete(beverage.id) } }
                assertIs<TopLevelCategory>(tx.inTransaction { repository.findById(beverage.id) })
            }
    }
}
