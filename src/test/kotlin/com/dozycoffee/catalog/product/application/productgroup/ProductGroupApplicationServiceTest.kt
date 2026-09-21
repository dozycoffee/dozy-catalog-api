package com.dozycoffee.catalog.product.application.productgroup

import com.dozycoffee.catalog.product.application.productgroup.command.RenameProductGroupCommand
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupRepository
import com.dozycoffee.catalog.product.domain.productgroup.exception.ProductGroupNotFoundException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("상품 그룹 관리 (요구사항 1.8)")
class ProductGroupApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ProductGroupApplicationService

    @Autowired
    private lateinit var productGroupRepository: ProductGroupRepository

    @Test
    fun `상품 그룹을 등록하고 조회한다`() =
        runTest {
            val group = service.register("여름 프로모션")

            assertEquals("여름 프로모션", tx { productGroupRepository.findById(group.id) }?.name)
        }

    @Test
    fun `이름 변경은 즉시 반영된다`() =
        runTest {
            val group = service.register("여름 프로모션")

            service.rename(RenameProductGroupCommand(group.id, "가을 프로모션"))

            assertEquals("가을 프로모션", tx { productGroupRepository.findById(group.id) }?.name)
        }

    @Test
    fun `그룹을 삭제하면 참조하던 상품에서도 함께 제거된다`() =
        runTest {
            val group = service.register("여름 프로모션")
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('아메리카노', 2, 4500, false)")
            execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (1, ${group.id.value})")

            service.delete(group.id)

            assertNull(tx { productGroupRepository.findById(group.id) })
            assertEquals(0, count("SELECT count(*) FROM product_groups_map"))
            assertEquals(1, count("SELECT count(*) FROM products"))
        }

    @Test
    fun `없는 그룹을 수정하거나 삭제하면 거부한다`() =
        runTest {
            assertFailsWith<ProductGroupNotFoundException> {
                service.rename(RenameProductGroupCommand(ProductGroupId(999), "가을 프로모션"))
            }
            assertFailsWith<ProductGroupNotFoundException> { service.delete(ProductGroupId(999)) }
        }

    @Nested
    @DisplayName("목록 조회")
    inner class Listing {
        @BeforeEach
        fun setUpGroups() =
            runTest {
                execute("INSERT INTO product_groups (name) VALUES ('여름 시즌'), ('가을 시즌'), ('겨울 시즌')")
            }

        @Test
        fun `조건이 없으면 모든 그룹을 등록 순으로 돌려준다`() =
            runTest {
                val groups = service.list()

                assertEquals(listOf(1L, 2L, 3L), groups.map { it.id.value })
                assertEquals(listOf("여름 시즌", "가을 시즌", "겨울 시즌"), groups.map { it.name })
            }

        @Test
        fun `ids를 주면 그 그룹만 등록 순으로 돌려주고 없는 ID는 빠진다`() =
            runTest {
                val groups = service.list(setOf(ProductGroupId(3), ProductGroupId(1), ProductGroupId(999)))

                assertEquals(listOf(1L, 3L), groups.map { it.id.value })
            }

        @Test
        fun `빈 ids는 아무것도 돌려주지 않는다`() =
            runTest {
                assertEquals(emptyList(), service.list(emptySet()))
            }

        @Test
        fun `100개를 넘는 ids는 호출 코드 오류로 거부한다`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    service.list((1L..101L).map(::ProductGroupId).toSet())
                }
            }
    }
}
