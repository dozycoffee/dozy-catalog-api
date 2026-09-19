package com.dozycoffee.catalog.infrastructure.persistence.productgroup

import com.dozycoffee.catalog.application.shared.TransactionRunner
import com.dozycoffee.catalog.domain.productgroup.ProductGroupRepository
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("ExposedProductGroupRepository")
class ExposedProductGroupRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: ProductGroupRepository

    @Test
    fun `저장한 상품 그룹을 조회할 수 있다`() =
        runTest {
            val created = tx.inTransaction { repository.insert("여름 프로모션") }

            val found = tx.inTransaction { repository.findById(created.id) }

            assertEquals("여름 프로모션", found?.name)
        }

    @Test
    fun `이름을 바꿔 저장하면 조회에 반영된다`() =
        runTest {
            val group = tx.inTransaction { repository.insert("여름 프로모션") }
            group.rename("가을 프로모션")

            tx.inTransaction { repository.save(group) }

            assertEquals("가을 프로모션", tx.inTransaction { repository.findById(group.id) }?.name)
        }

    @Test
    fun `상품 그룹을 삭제하면 상품과의 연결도 함께 삭제된다`() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('아메리카노', 2, 4500, false)")
            val group = tx.inTransaction { repository.insert("여름 프로모션") }
            execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (1, ${group.id.value})")

            tx.inTransaction { repository.delete(group.id) }

            assertNull(tx.inTransaction { repository.findById(group.id) })
            assertEquals(0, count("SELECT count(*) FROM product_groups_map"))
            assertEquals(1, count("SELECT count(*) FROM products"))
        }
}
