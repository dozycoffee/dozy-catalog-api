package com.dozycoffee.catalog.product.presentation.tag

import com.dozycoffee.catalog.product.presentation.expectProblem
import com.dozycoffee.catalog.support.ApiTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals

// 태그 API(docs/api/product.md 태그). 태그는 상품 등록·수정에서 생기므로 준비 데이터는 SQL로 넣는다.
// 준비 데이터: 1 신메뉴, 2 시즌한정, 3 베스트
@DisplayName("태그 API (요구사항 1.7)")
class TagApiTest : ApiTest() {
    @BeforeEach
    fun givenTags() =
        runBlocking {
            execute("INSERT INTO tags (name) VALUES ('신메뉴'), ('시즌한정'), ('베스트')")
        }

    @Test
    fun `목록은 이름 순이다`() {
        client
            .get()
            .uri("/api/v1/admin/tags")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].name")
            .isEqualTo(listOf("베스트", "시즌한정", "신메뉴"))
            .jsonPath("$[0].id")
            .isEqualTo(3)
    }

    @Test
    fun `목록을 ids와 keyword로 거른다`() {
        client
            .get()
            .uri("/api/v1/admin/tags?ids=1,2&keyword=시즌")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].id")
            .isEqualTo(listOf(2))
    }

    @Test
    fun `이름을 바꾸고 태그를 준다`() {
        rename(1, "새 메뉴")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(1)
            .jsonPath("$.name")
            .isEqualTo("새 메뉴")
    }

    @Test
    fun `다른 태그와 같은 이름으로 바꾸면 409 TAG_NAME_DUPLICATED`() {
        rename(1, "베스트").expectProblem(409, "TAG_NAME_DUPLICATED")
        assertEquals(1, countBlocking("SELECT count(*) FROM tags WHERE id = 1 AND name = '신메뉴'"))
    }

    @Test
    fun `없는 태그는 404 TAG_NOT_FOUND`() {
        rename(999, "베스트").expectProblem(404, "TAG_NOT_FOUND")
    }

    @Test
    fun `삭제하면 204`() {
        client
            .delete()
            .uri("/api/v1/admin/tags/2")
            .exchange()
            .expectStatus()
            .isNoContent
            .expectBody()
            .isEmpty
        assertEquals(0, countBlocking("SELECT count(*) FROM tags WHERE id = 2"))
    }

    private fun rename(
        tagId: Long,
        name: String,
    ): WebTestClient.ResponseSpec =
        client
            .put()
            .uri("/api/v1/admin/tags/$tagId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name": "$name"}""")
            .exchange()

    private fun countBlocking(sql: String): Long = runBlocking { count(sql) }
}
