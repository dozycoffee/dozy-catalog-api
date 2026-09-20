package com.dozycoffee.catalog.infrastructure.persistence.storeavailability

import com.dozycoffee.catalog.application.shared.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.storeavailability.AvailabilitySource
import com.dozycoffee.catalog.domain.storeavailability.StockStatus
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailabilityId
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.fixture.inventoryAvailability
import com.dozycoffee.catalog.fixture.ownerAvailability
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ExposedStoreProductAvailabilityRepository")
class ExposedStoreProductAvailabilityRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: StoreProductAvailabilityRepository

    private val store = StoreId(10)
    private val otherStore = StoreId(20)
    private val americano = ProductId(1) // 재고 미추적 상품
    private val tumbler = ProductId(2) // 재고 추적 상품
    private val eventAt = Instant.parse("2026-09-18T00:00:00Z")

    @BeforeEach
    fun prepareProducts() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('아메리카노', 2, 4500, false)")
            execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('텀블러', 2, 30000, true)")
        }

    @Nested
    @DisplayName("점주 수동 품절 (OWNER)")
    inner class Owner {
        @Test
        fun `처음 저장하면 만들고 조회할 수 있다`() =
            runTest {
                val availability = ownerAvailability(storeId = store, productId = americano, stockStatus = StockStatus.SOLD_OUT)

                tx.inTransaction { repository.saveByOwner(availability) }

                val found = assertNotNull(tx.inTransaction { repository.findById(availability.id) })
                assertEquals(AvailabilitySource.OWNER, found.source)
                assertEquals(StockStatus.SOLD_OUT, found.stockStatus)
                assertNull(found.lastEventAt)
            }

        @Test
        fun `다시 저장하면 점주의 최신 값으로 덮어쓴다`() =
            runTest {
                val availability = ownerAvailability(storeId = store, productId = americano, stockStatus = StockStatus.SOLD_OUT)
                tx.inTransaction { repository.saveByOwner(availability) }
                availability.changeByOwner(StockStatus.ON_SALE)

                tx.inTransaction { repository.saveByOwner(availability) }

                assertEquals(StockStatus.ON_SALE, tx.inTransaction { repository.findById(availability.id) }?.stockStatus)
                assertEquals(1, count("SELECT count(*) FROM store_product_availabilities"))
            }

        @Test
        fun `재고 추적 상품의 판매 가능 여부는 점주 저장 경로로 넘길 수 없다`() =
            runTest {
                val availability = inventoryAvailability(storeId = store, productId = tumbler)

                assertFailsWith<IllegalArgumentException> { tx.inTransaction { repository.saveByOwner(availability) } }
                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }

        @Test
        fun `OWNER 출처 행에 재고 이벤트 시각이 있으면 DB가 거부한다`() =
            runTest {
                assertFails {
                    execute(
                        """
                        INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status, last_event_at)
                        VALUES (10, 1, 'OWNER', 'ON_SALE', '2026-09-18T00:00:00Z')
                        """.trimIndent(),
                    )
                }
                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }
    }

    @Nested
    @DisplayName("재고 이벤트 반영 (INVENTORY)")
    inner class Inventory {
        @Test
        fun `첫 재고 이벤트는 행을 만들고 반영했다고 알린다`() =
            runTest {
                val availability =
                    inventoryAvailability(storeId = store, productId = tumbler, stockStatus = StockStatus.ON_SALE, occurredAt = eventAt)

                val applied = tx.inTransaction { repository.saveInventoryEvent(availability) }

                assertTrue(applied)
                val found = assertNotNull(tx.inTransaction { repository.findById(availability.id) })
                assertEquals(AvailabilitySource.INVENTORY, found.source)
                assertEquals(StockStatus.ON_SALE, found.stockStatus)
                assertEquals(eventAt, found.lastEventAt)
            }

        @Test
        fun `이미 반영한 것보다 새 이벤트는 반영한다`() =
            runTest {
                tx.inTransaction {
                    repository.saveInventoryEvent(
                        inventoryAvailability(
                            storeId = store,
                            productId = tumbler,
                            stockStatus = StockStatus.ON_SALE,
                            occurredAt = eventAt,
                        ),
                    )
                }
                val newer = eventAt.plusSeconds(60)

                val applied =
                    tx.inTransaction {
                        repository.saveInventoryEvent(
                            inventoryAvailability(
                                storeId = store,
                                productId = tumbler,
                                stockStatus = StockStatus.SOLD_OUT,
                                occurredAt = newer,
                            ),
                        )
                    }

                assertTrue(applied)
                val found = assertNotNull(tx.inTransaction { repository.findById(StoreProductAvailabilityId(store, tumbler)) })
                assertEquals(StockStatus.SOLD_OUT, found.stockStatus)
                assertEquals(newer, found.lastEventAt)
            }

        // 애그리거트가 오래된 이벤트를 걸러도, 그 사이 다른 트랜잭션이 더 새 이벤트를 반영했을 수 있다.
        // 메모리의 애그리거트가 그 사실을 모르는 상황을 만들어 DB 조건이 막는지 확인한다.
        @ParameterizedTest(name = "반영한 시각보다 {0}초 뒤")
        @ValueSource(longs = [-60, 0])
        fun `이미 반영한 것보다 오래되었거나 같은 시각의 이벤트는 DB에서도 반영하지 않는다`(offsetSeconds: Long) =
            runTest {
                tx.inTransaction {
                    repository.saveInventoryEvent(
                        inventoryAvailability(
                            storeId = store,
                            productId = tumbler,
                            stockStatus = StockStatus.ON_SALE,
                            occurredAt = eventAt,
                        ),
                    )
                }
                val stale =
                    inventoryAvailability(
                        storeId = store,
                        productId = tumbler,
                        stockStatus = StockStatus.SOLD_OUT,
                        occurredAt = eventAt.plusSeconds(offsetSeconds),
                    )

                val applied = tx.inTransaction { repository.saveInventoryEvent(stale) }

                assertFalse(applied)
                val found = assertNotNull(tx.inTransaction { repository.findById(stale.id) })
                assertEquals(StockStatus.ON_SALE, found.stockStatus)
                assertEquals(eventAt, found.lastEventAt)
            }

        @Test
        fun `재고 이벤트를 반영하지 않은 판매 가능 여부는 저장할 수 없다`() =
            runTest {
                val availability = inventoryAvailability(storeId = store, productId = tumbler)

                assertFailsWith<IllegalArgumentException> { tx.inTransaction { repository.saveInventoryEvent(availability) } }
                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }
    }

    @Nested
    @DisplayName("목록 조회와 삭제")
    inner class FindAllAndDelete {
        @Test
        fun `매장별, 상품별로 조회한다`() =
            runTest {
                tx.inTransaction {
                    repository.saveByOwner(ownerAvailability(storeId = store, productId = americano, stockStatus = StockStatus.SOLD_OUT))
                    repository.saveByOwner(
                        ownerAvailability(storeId = otherStore, productId = americano, stockStatus = StockStatus.SOLD_OUT),
                    )
                    repository.saveInventoryEvent(
                        inventoryAvailability(
                            storeId = store,
                            productId = tumbler,
                            stockStatus = StockStatus.ON_SALE,
                            occurredAt = eventAt,
                        ),
                    )
                }

                assertEquals(
                    listOf(StoreProductAvailabilityId(store, americano), StoreProductAvailabilityId(store, tumbler)),
                    tx.inTransaction { repository.findAllByStore(store) }.map { it.id },
                )
                assertEquals(
                    listOf(StoreProductAvailabilityId(store, americano), StoreProductAvailabilityId(otherStore, americano)),
                    tx.inTransaction { repository.findAllByProduct(americano) }.map { it.id },
                )
            }

        @Test
        fun `넘긴 ID의 행만 삭제한다`() =
            runTest {
                val removed = ownerAvailability(storeId = store, productId = americano, stockStatus = StockStatus.SOLD_OUT)
                val keptSameStore =
                    inventoryAvailability(storeId = store, productId = tumbler, stockStatus = StockStatus.ON_SALE, occurredAt = eventAt)
                val keptSameProduct = ownerAvailability(storeId = otherStore, productId = americano, stockStatus = StockStatus.SOLD_OUT)
                tx.inTransaction {
                    repository.saveByOwner(removed)
                    repository.saveInventoryEvent(keptSameStore)
                    repository.saveByOwner(keptSameProduct)
                }

                tx.inTransaction { repository.deleteAll(listOf(removed.id)) }

                assertNull(tx.inTransaction { repository.findById(removed.id) })
                assertNotNull(tx.inTransaction { repository.findById(keptSameStore.id) })
                assertNotNull(tx.inTransaction { repository.findById(keptSameProduct.id) })
            }

        @Test
        fun `상품을 삭제하면 그 상품의 판매 가능 여부도 함께 삭제된다`() =
            runTest {
                tx.inTransaction {
                    repository.saveByOwner(ownerAvailability(storeId = store, productId = americano, stockStatus = StockStatus.SOLD_OUT))
                    repository.saveInventoryEvent(
                        inventoryAvailability(
                            storeId = store,
                            productId = tumbler,
                            stockStatus = StockStatus.ON_SALE,
                            occurredAt = eventAt,
                        ),
                    )
                }

                execute("DELETE FROM products WHERE id = ${tumbler.value}")

                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities WHERE product_id = ${tumbler.value}"))
                assertEquals(1, count("SELECT count(*) FROM store_product_availabilities WHERE product_id = ${americano.value}"))
            }
    }
}
