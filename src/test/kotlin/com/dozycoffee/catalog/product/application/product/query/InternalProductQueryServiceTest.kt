package com.dozycoffee.catalog.product.application.product.query

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

// Store 서비스용 조회가 판매 범위(`product_target_stores`)를 읽지 않는지 확인한다(#130).
// 그 테이블의 이름을 바꿔 둔 채로 조회하면, 읽으려는 순간 오류가 나므로 읽지 않는다는 것이 결정적으로 드러난다.
// 상태 목록은 판매 범위가 응답 내용이라 같은 조건에서 실패해야 한다(읽어야 하는 곳은 그대로 읽는다는 확인).
@DisplayName("Store 서비스용 상품 조회 (#130)")
class InternalProductQueryServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: InternalProductQueryService

    @Autowired
    private lateinit var productRepository: ProductRepository

    // 1 아메리카노(전체 판매, 옵션 연결 하나), 2 한정 판매 상품(대상 매장 3개, 같은 옵션 그룹, 사이즈 라지 제외)
    private suspend fun prepare() {
        execute("INSERT INTO categories (name) VALUES ('음료')")
        execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
        execute("INSERT INTO tags (name) VALUES ('신메뉴')")
        execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('사이즈', 'SINGLE', true)")
        execute(
            "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                "VALUES (1, 'REGULAR', '레귤러', 0, 0), (1, 'LARGE', '라지', 500, 1)",
        )
        execute(
            "INSERT INTO products (sku, name, category_id, base_price, status, store_scope, tracks_inventory) VALUES " +
                "('DZ-00000001', '아메리카노', 2, 4500, 'ACTIVE', 'ALL', false), " +
                "('DZ-00000002', '한정 라떼', 2, 5000, 'ACTIVE', 'LIMITED', false)",
        )
        execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (2, 10), (2, 20), (2, 30)")
        execute("INSERT INTO product_tags (product_id, tag_id) VALUES (2, 1)")
        execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 1, 0), (2, 1, 0)")
        execute(
            "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price) " +
                "VALUES (2, 1, 'LARGE', 'EXCLUDE', NULL)",
        )
    }

    // 블록이 도는 동안 판매 범위 테이블을 읽을 수 없게 만든다. 끝나면(실패해도) 원래 이름으로 되돌린다.
    private suspend fun <T> withoutTargetStoresTable(block: suspend () -> T): T {
        execute("ALTER TABLE product_target_stores RENAME TO product_target_stores_hidden")
        try {
            return block()
        } finally {
            execute("ALTER TABLE product_target_stores_hidden RENAME TO product_target_stores")
        }
    }

    @Test
    fun `기준 정보 조회는 판매 범위를 읽지 않고도 한정 판매 상품의 값을 그대로 돌려준다`() =
        runTest {
            prepare()

            val views = withoutTargetStoresTable { service.summaries(setOf(ProductId(1), ProductId(2))) }

            assertEquals(listOf(1L, 2L), views.map { it.info.id.value })
            val limited = views.last()
            assertEquals("한정 라떼", limited.info.name)
            assertEquals(listOf("신메뉴"), limited.tags.map { it.name })
            // 기준가 5000 + 필수 그룹의 선택 가능한 최저가(라지 제외, 레귤러 0)
            assertEquals(5000, limited.displayStartingPrice.amount)
        }

    @Test
    fun `유효 옵션 조회도 판매 범위를 읽지 않고 상품별 예외를 반영한다`() =
        runTest {
            prepare()

            val config = withoutTargetStoresTable { service.getEffectiveOptions(ProductId(2)) }

            assertEquals(
                listOf("REGULAR"),
                config.groups
                    .single()
                    .options
                    .map { it.optionKey.value },
            )
        }

    @Test
    fun `없는 상품은 기준 정보에서 빠지고 유효 옵션에서는 거부된다`() =
        runTest {
            prepare()

            assertEquals(emptyList(), service.summaries(setOf(ProductId(999))))
            assertEquals(emptyList(), service.summaries(emptySet()))
            assertFailsWith<ProductNotFoundException> { service.getEffectiveOptions(ProductId(999)) }
        }

    @Test
    fun `이 확인 방법이 유효한지 - 상품 애그리거트로 복원하면 판매 범위 테이블을 읽는다`() =
        runTest {
            prepare()

            // 옛 구현(상품 애그리거트로 복원)은 판매 범위를 쓰지 않으면서도 읽어서, 같은 조건에서 실패한다.
            assertFails { withoutTargetStoresTable { tx { productRepository.findAllByIds(setOf(ProductId(2))) } } }
        }

    @Test
    fun `상태 목록은 판매 범위가 응답 내용이라 대상 매장을 읽는다`() =
        runTest {
            prepare()

            val page = service.states(after = null, size = 10)

            val scope = assertIs<StoreScope.Limited>(page.products.single { it.id == ProductId(2) }.storeScope)
            assertEquals(setOf(StoreId(10), StoreId(20), StoreId(30)), scope.targetStoreIds)
        }
}
