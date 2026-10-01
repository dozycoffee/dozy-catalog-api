package com.dozycoffee.catalog.product.presentation.productgroup

import com.dozycoffee.catalog.product.presentation.expectProblem
import com.dozycoffee.catalog.support.ApiTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import kotlin.test.assertEquals

// 상품 그룹 API(docs/api/product.md 상품 그룹).
@DisplayName("상품 그룹 API (요구사항 1.8)")
class ProductGroupApiTest : ApiTest() {
    @Test
    fun `목록은 등록 순이고 ids로 거른다`() {
        givenProductGroups("여름 시즌", "가을 시즌", "MD 기획")

        client
            .get()
            .uri("/api/v1/admin/product-groups")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].name")
            .isEqualTo(listOf("여름 시즌", "가을 시즌", "MD 기획"))
            .jsonPath("$[0].id")
            .isEqualTo(1)

        client
            .get()
            .uri("/api/v1/admin/product-groups?ids=3,1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].id")
            .isEqualTo(listOf(1, 3))
    }

    @Test
    fun `등록하면 201과 Location과 함께 상품 그룹을 준다`() {
        client
            .post()
            .uri("/api/v1/admin/product-groups")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name": "여름 시즌"}""")
            .exchange()
            .expectStatus()
            .isCreated
            .expectHeader()
            .location("/api/v1/admin/product-groups/1")
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(1)
            .jsonPath("$.name")
            .isEqualTo("여름 시즌")
    }

    @Test
    fun `이름을 바꾸고 상품 그룹을 준다`() {
        givenProductGroups("여름 시즌")

        client
            .put()
            .uri("/api/v1/admin/product-groups/1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name": "한여름 시즌"}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(1)
            .jsonPath("$.name")
            .isEqualTo("한여름 시즌")
    }

    @Test
    fun `삭제하면 204`() {
        givenProductGroups("여름 시즌")

        client
            .delete()
            .uri("/api/v1/admin/product-groups/1")
            .exchange()
            .expectStatus()
            .isNoContent
            .expectBody()
            .isEmpty
        assertEquals(0, runBlocking { count("SELECT count(*) FROM product_groups") })
    }

    @Test
    fun `없는 상품 그룹은 404 PRODUCT_GROUP_NOT_FOUND`() {
        client
            .delete()
            .uri("/api/v1/admin/product-groups/999")
            .exchange()
            .expectProblem(404, "PRODUCT_GROUP_NOT_FOUND")
    }

    private fun givenProductGroups(vararg names: String) =
        runBlocking {
            names.forEach { execute("INSERT INTO product_groups (name) VALUES ('$it')") }
        }
}
