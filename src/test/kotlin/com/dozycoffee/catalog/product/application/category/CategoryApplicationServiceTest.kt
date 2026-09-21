package com.dozycoffee.catalog.product.application.category

import com.dozycoffee.catalog.product.application.category.command.ChangeCategoryParentCommand
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.category.command.RenameCategoryCommand
import com.dozycoffee.catalog.product.application.category.query.CategoryFilter
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.category.CategoryRepository
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.TopLevelCategory
import com.dozycoffee.catalog.product.domain.category.exception.CategoryHasChildrenException
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotFoundException
import com.dozycoffee.catalog.product.domain.category.exception.CategoryStillReferencedException
import com.dozycoffee.catalog.product.domain.category.exception.CategoryWithChildrenNotDemotableException
import com.dozycoffee.catalog.product.domain.category.exception.ReferencedCategoryNotPromotableException
import com.dozycoffee.catalog.product.domain.category.exception.TopLevelCategoryNotFoundException
import com.dozycoffee.catalog.support.ApplicationTest
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

@DisplayName("카테고리 관리 (요구사항 1.6)")
class CategoryApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: CategoryApplicationService

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Nested
    @DisplayName("등록")
    inner class Registration {
        @Test
        fun `대분류를 등록한다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")

                assertIs<TopLevelCategory>(find(beverage.id))
                assertEquals("음료", find(beverage.id)?.name)
            }

        @Test
        fun `대분류 아래에 소분류를 등록한다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")

                val coffee = service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))

                val found = assertIs<ChildCategory>(find(coffee.id))
                assertEquals(beverage.id, found.parentId)
            }

        @Test
        fun `소분류를 부모로 지정하면 거부한다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                val coffee = service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))

                assertFailsWith<TopLevelCategoryNotFoundException> {
                    service.registerChild(RegisterChildCategoryCommand(coffee.id, "에스프레소"))
                }
                assertEquals(2, count("SELECT count(*) FROM categories"))
            }

        @Test
        fun `없는 카테고리를 부모로 지정하면 거부한다`() =
            runTest {
                assertFailsWith<TopLevelCategoryNotFoundException> {
                    service.registerChild(RegisterChildCategoryCommand(CategoryId(999), "커피"))
                }
            }
    }

    @Test
    fun `이름 변경은 즉시 반영된다`() =
        runTest {
            val beverage = service.registerTopLevel("음료")

            service.rename(RenameCategoryCommand(beverage.id, "음료류"))

            assertEquals("음료류", find(beverage.id)?.name)
        }

    @Nested
    @DisplayName("계층 변경")
    inner class HierarchyChange {
        @Test
        fun `소분류를 다른 대분류로 옮긴다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                val dessert = service.registerTopLevel("디저트")
                val coffee = service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))

                service.changeParent(ChangeCategoryParentCommand(coffee.id, dessert.id))

                val found = assertIs<ChildCategory>(find(coffee.id))
                assertEquals(dessert.id, found.parentId)
            }

        @Test
        fun `하위 카테고리가 없는 대분류는 소분류로 내릴 수 있다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                val tea = service.registerTopLevel("차")

                service.changeParent(ChangeCategoryParentCommand(tea.id, beverage.id))

                val found = assertIs<ChildCategory>(find(tea.id))
                assertEquals(beverage.id, found.parentId)
            }

        @Test
        fun `하위 카테고리를 가진 대분류는 소분류로 내릴 수 없다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                val dessert = service.registerTopLevel("디저트")
                service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))

                assertFailsWith<CategoryWithChildrenNotDemotableException> {
                    service.changeParent(ChangeCategoryParentCommand(beverage.id, dessert.id))
                }
                assertIs<TopLevelCategory>(find(beverage.id))
            }

        @Test
        fun `참조하는 상품이 없는 소분류는 대분류로 올릴 수 있다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                val coffee = service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))

                service.promoteToTopLevel(coffee.id)

                assertIs<TopLevelCategory>(find(coffee.id))
            }

        @Test
        fun `상품이 참조하는 소분류는 대분류로 올릴 수 없다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                val coffee = service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))
                insertProduct(coffee.id)

                assertFailsWith<ReferencedCategoryNotPromotableException> { service.promoteToTopLevel(coffee.id) }
                assertIs<ChildCategory>(find(coffee.id))
            }
    }

    @Nested
    @DisplayName("삭제")
    inner class Deletion {
        @Test
        fun `참조가 없는 카테고리는 삭제한다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")

                service.delete(beverage.id)

                assertNull(find(beverage.id))
            }

        @Test
        fun `하위 카테고리를 가진 대분류는 삭제할 수 없다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))

                assertFailsWith<CategoryHasChildrenException> { service.delete(beverage.id) }
                assertEquals(2, count("SELECT count(*) FROM categories"))
            }

        @Test
        fun `상품이 참조하는 소분류는 삭제할 수 없다`() =
            runTest {
                val beverage = service.registerTopLevel("음료")
                val coffee = service.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))
                insertProduct(coffee.id)

                assertFailsWith<CategoryStillReferencedException> { service.delete(coffee.id) }
                assertEquals(2, count("SELECT count(*) FROM categories"))
            }

        @Test
        fun `없는 카테고리를 삭제하면 거부한다`() =
            runTest {
                assertFailsWith<CategoryNotFoundException> { service.delete(CategoryId(999)) }
            }
    }

    @Nested
    @DisplayName("목록 조회")
    inner class Listing {
        // 1 음료, 2 푸드는 대분류이고 3 커피, 5 차는 음료의, 4 빵은 푸드의 소분류다.
        @BeforeEach
        fun setUpCategories() =
            runTest {
                execute("INSERT INTO categories (name) VALUES ('음료'), ('푸드')")
                execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1), ('빵', 2), ('차', 1)")
            }

        @Test
        fun `조건이 없으면 대분류와 소분류를 평평한 목록으로 등록 순으로 돌려준다`() =
            runTest {
                val categories = service.list(CategoryFilter())

                assertEquals(listOf(1L, 2L, 3L, 4L, 5L), categories.map { it.id.value })
                assertEquals("음료", assertIs<TopLevelCategory>(categories[0]).name)
                val coffee = assertIs<ChildCategory>(categories[2])
                assertEquals("커피", coffee.name)
                assertEquals(CategoryId(1), coffee.parentId)
            }

        @Test
        fun `parentId를 주면 그 대분류의 소분류만 돌려준다`() =
            runTest {
                assertEquals(listOf(3L, 5L), ids(CategoryFilter(parentId = CategoryId(1))))
            }

        @Test
        fun `topLevel이면 대분류만 돌려준다`() =
            runTest {
                assertEquals(listOf(1L, 2L), ids(CategoryFilter(topLevelOnly = true)))
            }

        @Test
        fun `ids를 주면 그 카테고리만 등록 순으로 돌려주고 없는 ID는 빠진다`() =
            runTest {
                val filter = CategoryFilter(ids = setOf(CategoryId(5), CategoryId(1), CategoryId(999)))

                assertEquals(listOf(1L, 5L), ids(filter))
            }

        @Test
        fun `빈 ids는 아무것도 돌려주지 않는다`() =
            runTest {
                assertEquals(emptyList(), ids(CategoryFilter(ids = emptySet())))
            }

        @Test
        fun `여러 조건을 함께 주면 모두 만족하는 것만 남는다`() =
            runTest {
                val idsAndParent = CategoryFilter(ids = setOf(CategoryId(3), CategoryId(4)), parentId = CategoryId(1))
                val idsAndTopLevel = CategoryFilter(ids = setOf(CategoryId(2), CategoryId(4)), topLevelOnly = true)
                val parentAndTopLevel = CategoryFilter(parentId = CategoryId(1), topLevelOnly = true)

                assertEquals(listOf(3L), ids(idsAndParent))
                assertEquals(listOf(2L), ids(idsAndTopLevel))
                assertEquals(emptyList(), ids(parentAndTopLevel))
            }

        @Test
        fun `100개를 넘는 ids는 호출 코드 오류로 거부한다`() {
            assertFailsWith<IllegalArgumentException> {
                CategoryFilter(ids = (1L..101L).map(::CategoryId).toSet())
            }
        }

        private suspend fun ids(filter: CategoryFilter) = service.list(filter).map { it.id.value }
    }

    private suspend fun find(id: CategoryId) = tx { categoryRepository.findById(id) }

    private suspend fun insertProduct(categoryId: CategoryId) =
        execute(
            "INSERT INTO products (name, category_id, base_price, tracks_inventory) " +
                "VALUES ('아메리카노', ${categoryId.value}, 4500, false)",
        )
}
