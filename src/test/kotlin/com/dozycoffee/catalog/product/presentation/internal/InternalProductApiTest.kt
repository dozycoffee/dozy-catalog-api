package com.dozycoffee.catalog.product.presentation.internal

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.WithDozyPrincipal
import com.dozycoffee.catalog.support.ApiTest
import com.dozycoffee.catalog.support.expectProblem
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

// Store 서비스가 부르는 상품 조회 API(docs/api/store-service.md). 규칙 자체는 다른 테스트가 덮으므로
// 응답 형식, 상태와 무관한 조회, 본사 내부 정보가 없는지, 커서 페이징을 확인한다. 준비 데이터는 SQL로 직접 넣는다.
@DisplayName("Store 서비스용 내부 조회 API")
@WithDozyPrincipal(type = PrincipalType.SYSTEM, roles = ["catalog:store_agent"])
class InternalProductApiTest : ApiTest() {
    private val base = "/api/v1/internal"

    // 1 아메리카노(Active, 사이즈 필수·온도 필수 연결, 태그 둘, 그룹 하나), 2 카페라떼(Draft), 3 홍대·강남 한정 텀블러(Discontinued, 재고 추적)
    @BeforeEach
    fun prepare() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료'), ('푸드')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1), ('디저트', 2), ('차', 1)")
            execute("INSERT INTO tags (name) VALUES ('신메뉴'), ('베스트')")
            execute("INSERT INTO product_groups (name) VALUES ('여름 시즌')")
            execute(
                """
                INSERT INTO option_groups (name, selection_type, required)
                VALUES ('사이즈', 'SINGLE', true), ('샷', 'MULTI', false), ('온도', 'SINGLE', true)
                """.trimIndent(),
            )
            execute(
                """
                INSERT INTO options (option_group_id, option_key, name, price, display_order)
                VALUES (1, 'REGULAR', '레귤러', 0, 0), (1, 'LARGE', '라지', 500, 1),
                       (2, 'SHOT', '샷 추가', 500, 0),
                       (3, 'ICE', '아이스', 300, 0)
                """.trimIndent(),
            )
            execute(
                """
                INSERT INTO products (sku, name, category_id, image_url, base_price, status, store_scope, tracks_inventory)
                VALUES ('DZ-00000001', '아메리카노', 3, 'https://cdn.example.com/p/1.png', 4500, 'ACTIVE', 'ALL', false),
                       ('DZ-00000002', '카페라떼', 3, NULL, 5000, 'DRAFT', 'ALL', false),
                       (NULL, '텀블러', 4, NULL, 25000, 'DISCONTINUED', 'LIMITED', true)
                """.trimIndent(),
            )
            execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (3, 20), (3, 10)")
            execute("INSERT INTO product_tags (product_id, tag_id) VALUES (1, 1), (1, 2)")
            execute("INSERT INTO product_groups_map (product_id, group_id) VALUES (1, 1)")
            execute("INSERT INTO product_option_groups (product_id, option_group_id, display_order) VALUES (1, 1, 0), (1, 2, 1), (1, 3, 2)")
        }

    @Nested
    @DisplayName("GET /products — 상품 기준 정보")
    inner class Products {
        @Test
        fun `기준 정보와 표시용 시작가와 태그를 담고 본사 내부 정보는 담지 않는다`() {
            val body =
                client
                    .get()
                    .uri("$base/products?ids=1")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.length()")
                    .isEqualTo(1)
                    .jsonPath("$[0].productId")
                    .isEqualTo(1)
                    .jsonPath("$[0].sku")
                    .isEqualTo("DZ-00000001")
                    .jsonPath("$[0].name")
                    .isEqualTo("아메리카노")
                    .jsonPath("$[0].categoryId")
                    .isEqualTo(3)
                    .jsonPath("$[0].imageUrl")
                    .isEqualTo("https://cdn.example.com/p/1.png")
                    .jsonPath("$[0].basePrice")
                    .isEqualTo(4500)
                    .jsonPath("$[0].tracksInventory")
                    .isEqualTo(false)
                    // 기준가 4500 + 사이즈(필수) 최저가 0 + 온도(필수) 300. 샷은 선택 그룹이라 더하지 않는다.
                    .jsonPath("$[0].displayStartingPrice")
                    .isEqualTo(4800)
                    // 태그는 이름 순이다(가나다순).
                    .jsonPath("$[0].tags[*].name")
                    .isEqualTo(listOf("베스트", "신메뉴"))
                    .returnResult()
                    .responseBody!!
                    .toString(Charsets.UTF_8)

            listOf("groupIds", "storeScope", "version", "status", "description").forEach { assertFalse(body.contains("\"$it\""), it) }
        }

        @Test
        fun `상태와 무관하게 조회되고 없는 ID는 빠진다`() {
            client
                .get()
                .uri("$base/products?ids=3,2,999")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[*].productId")
                .isEqualTo(listOf(2, 3))
                // SKU가 없는 상품, 옵션·태그가 없는 상품도 같은 모양이다.
                .jsonPath("$[1].sku")
                .isEqualTo(null)
                .jsonPath("$[1].displayStartingPrice")
                .isEqualTo(25000)
                .jsonPath("$[1].tags.length()")
                .isEqualTo(0)
        }

        @Test
        fun `ids가 없거나 100개를 넘거나 형식이 틀리면 400`() {
            client
                .get()
                .uri("$base/products")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
            client
                .get()
                .uri("$base/products?ids=${(1..101).joinToString(",")}")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
            client
                .get()
                .uri("$base/products?ids=1,abc")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
        }

        @Test
        fun `ids가 비어 있으면 빈 목록이다`() {
            client
                .get()
                .uri("$base/products?ids=")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("GET /categories — 카테고리 트리")
    inner class Categories {
        @Test
        fun `대분류마다 소분류를 담는다`() {
            client
                .get()
                .uri("$base/categories")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[*].name")
                .isEqualTo(listOf("음료", "푸드"))
                .jsonPath("$[0].children[*].name")
                .isEqualTo(listOf("커피", "차"))
                .jsonPath("$[1].children[*].id")
                .isEqualTo(listOf(4))
        }
    }

    @Nested
    @DisplayName("GET /products/{productId}/effective-options — 유효 옵션 구성")
    inner class EffectiveOptions {
        @Test
        fun `매장과 상품 상태를 보지 않고 옵션 구성과 표시용 시작가를 돌려준다`() {
            client
                .get()
                .uri("$base/products/1/effective-options")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.productId")
                .isEqualTo(1)
                .jsonPath("$.displayStartingPrice")
                .isEqualTo(4800)
                .jsonPath("$.groups[*].optionGroupId")
                .isEqualTo(listOf(1, 2, 3))
                .jsonPath("$.groups[2].autoSelectedOptionKey")
                .isEqualTo("ICE")
        }

        @Test
        fun `Draft 상품도 계산해 준다 — 판매 가능 여부는 Store가 먼저 판단한다`() {
            client
                .get()
                .uri("$base/products/2/effective-options")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.displayStartingPrice")
                .isEqualTo(5000)
        }

        @Test
        fun `없는 상품이면 404 PRODUCT_NOT_FOUND`() {
            client
                .get()
                .uri("$base/products/999/effective-options")
                .exchange()
                .expectProblem(404, "PRODUCT_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("GET /product-states — 상품 상태 전체 (커서 페이징)")
    inner class ProductStates {
        @Test
        fun `이벤트 data와 같은 모양과 sequence를 id 순으로 담는다`() {
            client
                .get()
                .uri("$base/product-states")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.content[*].productId")
                .isEqualTo(listOf(1, 2, 3))
                .jsonPath("$.nextCursor")
                .isEqualTo(null)
                .jsonPath("$.content[0].sku")
                .isEqualTo("DZ-00000001")
                .jsonPath("$.content[0].status")
                .isEqualTo("ACTIVE")
                .jsonPath("$.content[0].storeScope.kind")
                .isEqualTo("ALL")
                .jsonPath("$.content[0].storeScope.targetStoreIds")
                .isEqualTo(null)
                .jsonPath("$.content[0].tracksInventory")
                .isEqualTo(false)
                .jsonPath("$.content[0].sequence")
                .isEqualTo(0)
                .jsonPath("$.content[1].status")
                .isEqualTo("DRAFT")
                .jsonPath("$.content[2].status")
                .isEqualTo("DISCONTINUED")
                .jsonPath("$.content[2].sku")
                .isEqualTo(null)
                // 대상 매장은 오름차순이다.
                .jsonPath("$.content[2].storeScope.kind")
                .isEqualTo("LIMITED")
                .jsonPath("$.content[2].storeScope.targetStoreIds")
                .isEqualTo(listOf(10, 20))
        }

        @Test
        fun `sequence는 상품이 저장될 때마다 오른 버전이다`() =
            runTest {
                execute("UPDATE products SET version = 7 WHERE id = 2")

                client
                    .get()
                    .uri("$base/product-states")
                    .exchange()
                    .expectBody()
                    .jsonPath("$.content[1].sequence")
                    .isEqualTo(7)
            }

        @Test
        fun `커서를 따라가면 빠짐없이 한 번씩 받고 마지막 페이지의 nextCursor는 null이다`() {
            client
                .get()
                .uri("$base/product-states?size=2")
                .exchange()
                .expectBody()
                .jsonPath("$.content[*].productId")
                .isEqualTo(listOf(1, 2))
                .jsonPath("$.nextCursor")
                .isEqualTo(2)

            client
                .get()
                .uri("$base/product-states?size=2&cursor=2")
                .exchange()
                .expectBody()
                .jsonPath("$.content[*].productId")
                .isEqualTo(listOf(3))
                .jsonPath("$.nextCursor")
                .isEqualTo(null)
        }

        @Test
        fun `삭제된 상품은 빠진다`() =
            runTest {
                execute("DELETE FROM products WHERE id = 2")

                client
                    .get()
                    .uri("$base/product-states")
                    .exchange()
                    .expectBody()
                    .jsonPath("$.content[*].productId")
                    .isEqualTo(listOf(1, 3))
            }

        @Test
        fun `size나 cursor가 범위를 벗어나면 400`() {
            client
                .get()
                .uri("$base/product-states?size=0")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
            client
                .get()
                .uri("$base/product-states?size=501")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
            client
                .get()
                .uri("$base/product-states?cursor=0")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
            client
                .get()
                .uri("$base/product-states?cursor=abc")
                .exchange()
                .expectProblem(400, "INVALID_REQUEST")
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        @Test
        @WithDozyPrincipal(roles = ["catalog:admin"])
        fun `본사 직원은 내부 API를 부를 수 없다`() {
            client
                .get()
                .uri("$base/categories")
                .exchange()
                .expectStatus()
                .isForbidden
        }
    }
}
