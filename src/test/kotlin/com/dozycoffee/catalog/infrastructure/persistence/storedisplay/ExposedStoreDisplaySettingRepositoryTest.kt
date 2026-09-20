package com.dozycoffee.catalog.infrastructure.persistence.storedisplay

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.storedisplay.StoreDisplaySettingRepository
import com.dozycoffee.catalog.domain.storedisplay.model.Visibility
import com.dozycoffee.catalog.support.IntegrationTest
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
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("ExposedStoreDisplaySettingRepository")
class ExposedStoreDisplaySettingRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: StoreDisplaySettingRepository

    private val store = StoreId(10)
    private val otherStore = StoreId(20)
    private val americano = ProductId(1)
    private val latte = ProductId(2)

    @BeforeEach
    fun prepareProducts() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('아메리카노', 2, 4500, false)")
            execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('카페라떼', 2, 5000, false)")
        }

    @Nested
    @DisplayName("Lazy 생성")
    inner class FindOrCreate {
        @Test
        fun `없으면 기본값(노출, 진열 순서 없음)으로 만든다`() =
            runTest {
                val created = tx.inTransaction { repository.findOrCreate(store, americano) }

                assertEquals(store, created.storeId)
                assertEquals(americano, created.productId)
                assertEquals(Visibility.VISIBLE, created.visibility)
                assertNull(created.displayOrder)
                assertEquals(1, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `이미 있으면 새로 만들지 않고 저장된 값을 돌려준다`() =
            runTest {
                val first = tx.inTransaction { repository.findOrCreate(store, americano) }
                first.hide()
                tx.inTransaction { repository.saveVisibility(first) }

                val second = tx.inTransaction { repository.findOrCreate(store, americano) }

                assertEquals(first.id, second.id)
                assertEquals(Visibility.HIDDEN, second.visibility)
                assertEquals(1, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `동시에 처음 만들어도 진열 설정은 매장·상품당 하나만 생긴다`() =
            runTest {
                val settings =
                    coroutineScope {
                        (1..10)
                            .map { async(Dispatchers.IO) { tx.inTransaction { repository.findOrCreate(store, americano) } } }
                            .awaitAll()
                    }

                assertEquals(1, settings.map { it.id }.distinct().size)
                assertEquals(1, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `같은 매장·상품의 행을 직접 두 번 넣으면 DB가 거부한다`() =
            runTest {
                execute("INSERT INTO store_display_settings (store_id, product_id) VALUES (10, 1)")

                assertFails { execute("INSERT INTO store_display_settings (store_id, product_id) VALUES (10, 1)") }
            }
    }

    @Nested
    @DisplayName("바뀐 필드만 저장")
    inner class SaveChangedField {
        @Test
        fun `노출 여부와 진열 순서를 저장하면 조회에 반영된다`() =
            runTest {
                val setting = tx.inTransaction { repository.findOrCreate(store, americano) }
                setting.hide()
                setting.changeDisplayOrder(3)

                tx.inTransaction {
                    repository.saveVisibility(setting)
                    repository.saveDisplayOrder(setting)
                }

                val found = assertNotNull(tx.inTransaction { repository.findByStoreAndProduct(store, americano) })
                assertEquals(Visibility.HIDDEN, found.visibility)
                assertEquals(3, found.displayOrder)
            }

        @Test
        fun `같은 설정을 따로 불러와 서로 다른 필드를 바꿔도 서로 덮어쓰지 않는다`() =
            runTest {
                val created = tx.inTransaction { repository.findOrCreate(store, americano) }
                // 두 요청이 같은 시점의 설정(노출, 진열 순서 없음)을 각각 불러온 상황
                val hidingRequest = assertNotNull(tx.inTransaction { repository.findById(created.id) })
                val reorderingRequest = assertNotNull(tx.inTransaction { repository.findById(created.id) })

                hidingRequest.hide()
                tx.inTransaction { repository.saveVisibility(hidingRequest) }
                reorderingRequest.changeDisplayOrder(5)
                tx.inTransaction { repository.saveDisplayOrder(reorderingRequest) }

                val found = assertNotNull(tx.inTransaction { repository.findById(created.id) })
                assertEquals(Visibility.HIDDEN, found.visibility)
                assertEquals(5, found.displayOrder)
            }
    }

    @Nested
    @DisplayName("목록 조회")
    inner class FindAll {
        @Test
        fun `매장별, 상품별로 조회한다`() =
            runTest {
                val storeAmericano = tx.inTransaction { repository.findOrCreate(store, americano) }
                val storeLatte = tx.inTransaction { repository.findOrCreate(store, latte) }
                val otherStoreAmericano = tx.inTransaction { repository.findOrCreate(otherStore, americano) }

                assertEquals(
                    listOf(storeAmericano.id, storeLatte.id),
                    tx.inTransaction { repository.findAllByStore(store) }.map { it.id },
                )
                assertEquals(
                    listOf(storeAmericano.id, otherStoreAmericano.id),
                    tx.inTransaction { repository.findAllByProduct(americano) }.map { it.id },
                )
            }

        @Test
        fun `설정이 없으면 null을 돌려준다`() =
            runTest {
                assertNull(tx.inTransaction { repository.findByStoreAndProduct(store, americano) })
            }
    }

    @Nested
    @DisplayName("삭제")
    inner class Delete {
        @Test
        fun `넘긴 ID의 설정만 삭제한다`() =
            runTest {
                val removed = tx.inTransaction { repository.findOrCreate(store, americano) }
                val kept = tx.inTransaction { repository.findOrCreate(otherStore, americano) }

                tx.inTransaction { repository.deleteAll(listOf(removed.id)) }

                assertNull(tx.inTransaction { repository.findById(removed.id) })
                assertNotNull(tx.inTransaction { repository.findById(kept.id) })
            }

        @Test
        fun `상품을 삭제하면 그 상품의 진열 설정도 함께 삭제된다`() =
            runTest {
                tx.inTransaction { repository.findOrCreate(store, americano) }
                tx.inTransaction { repository.findOrCreate(store, latte) }

                execute("DELETE FROM products WHERE id = ${americano.value}")

                assertEquals(0, count("SELECT count(*) FROM store_display_settings WHERE product_id = ${americano.value}"))
                assertEquals(1, count("SELECT count(*) FROM store_display_settings WHERE product_id = ${latte.value}"))
            }
    }
}
