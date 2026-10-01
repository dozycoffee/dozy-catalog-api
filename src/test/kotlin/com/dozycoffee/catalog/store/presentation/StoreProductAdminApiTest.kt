package com.dozycoffee.catalog.store.presentation

import com.dozycoffee.catalog.support.ApiTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

// 본사관리자의 매장 상품 조회(docs/api/store.md). 매장 상품 목록과 같은 조회·응답이라 형식은 StoreProductInternalApiTest가 덮고,
// 여기서는 본사 경로로 같은 결과를 받는지와 본사 직원이 내부 API를 부를 수 없는지를 확인한다.
@DisplayName("본사관리자의 매장 상품 조회 API")
class StoreProductAdminApiTest : ApiTest() {
    @BeforeEach
    fun prepareProducts() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            insertProduct("아메리카노", storeScope = "ALL")
            insertProduct("카페라떼", storeScope = "ALL")
            insertProduct("홍대 한정 상품", storeScope = "LIMITED")
            execute("INSERT INTO store_display_settings (store_id, product_id, display_order) VALUES ($GANGNAM, 2, 1)")
        }

    @Test
    fun `한 매장의 상품 상태를 매장 상품 목록과 같은 형식과 순서로 돌려준다`() {
        client
            .get()
            .uri("/api/v1/admin/stores/$GANGNAM/products")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].productId")
            .isEqualTo(listOf(2, 1))
            .jsonPath("$[0].displayOrder")
            .isEqualTo(1)
            .jsonPath("$[0].visibility")
            .isEqualTo("VISIBLE")
            .jsonPath("$[0].stockStatus")
            .isEqualTo("ON_SALE")
            .jsonPath("$[0].status")
            .doesNotExist()
    }

    @Test
    fun `ids로 거를 수 있다`() {
        client
            .get()
            .uri("/api/v1/admin/stores/$GANGNAM/products?ids=1,3")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].productId")
            .isEqualTo(listOf(1))
    }

    @Test
    fun `본사 직원은 Store 서비스의 내부 API를 부를 수 없다`() {
        client
            .get()
            .uri("/api/v1/internal/stores/$GANGNAM/products")
            .exchange()
            .expectStatus()
            .isForbidden
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("FORBIDDEN")
    }

    private suspend fun insertProduct(
        name: String,
        storeScope: String,
    ) = execute(
        """
        INSERT INTO products (name, category_id, base_price, status, store_scope, tracks_inventory)
        VALUES ('$name', 2, 4500, 'ACTIVE', '$storeScope', false)
        """.trimIndent(),
    )

    private companion object {
        const val GANGNAM = 10L
    }
}
