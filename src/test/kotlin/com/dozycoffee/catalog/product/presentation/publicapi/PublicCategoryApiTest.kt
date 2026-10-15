package com.dozycoffee.catalog.product.presentation.publicapi

import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient

// 토큰 없이 읽는 공개 카테고리 API(docs/api/public.md, ADR-0022). 토큰 없는 요청이 핵심이라 요청자를 붙이는 ApiTest를 쓰지 않는다.
@DisplayName("공개 카테고리 API")
@AutoConfigureWebTestClient
class PublicCategoryApiTest : IntegrationTest() {
    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var tokens: DozyTestTokens

    private val path = "/api/v1/public/categories"

    // 1 음료(커피 3, 차 5), 2 푸드(디저트 4)
    @BeforeEach
    fun prepare() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료'), ('푸드')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1), ('디저트', 2), ('차', 1)")
        }

    @Test
    fun `토큰 없이 카테고리 트리를 읽는다`() {
        client
            .get()
            .uri(path)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].name")
            .isEqualTo(listOf("음료", "푸드"))
            .jsonPath("$[0].children[*].name")
            .isEqualTo(listOf("커피", "차"))
    }

    @Test
    fun `본사가 정한 노출 순서를 따른다`() =
        runTest {
            execute("UPDATE categories SET display_order = 1 WHERE id = 2")
            execute("UPDATE categories SET display_order = 2 WHERE id = 1")

            client
                .get()
                .uri(path)
                .exchange()
                .expectBody()
                .jsonPath("$[*].name")
                .isEqualTo(listOf("푸드", "음료"))
        }

    @Test
    fun `공개 캐시를 허용하고 10분 동안 다시 묻지 않게 한다`() {
        client
            .get()
            .uri(path)
            .exchange()
            .expectHeader()
            .valueEquals(HttpHeaders.CACHE_CONTROL, "max-age=600, public")
    }

    @Test
    fun `토큰을 보내도 같은 응답이다`() {
        client
            .get()
            .uri(path)
            .headers { it.setBearerAuth(tokens.issue(roles = listOf("catalog:admin"))) }
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `읽기 외의 요청은 공개되지 않는다`() {
        client
            .post()
            .uri(path)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `본사 API와 내부 API는 여전히 토큰이 필요하다`() {
        client
            .get()
            .uri("/api/v1/admin/categories")
            .exchange()
            .expectStatus()
            .isUnauthorized
        client
            .get()
            .uri("/api/v1/internal/categories")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }
}
