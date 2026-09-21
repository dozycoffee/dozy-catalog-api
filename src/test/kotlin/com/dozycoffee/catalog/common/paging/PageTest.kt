package com.dozycoffee.catalog.common.paging

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("페이징")
class PageTest {
    @Nested
    @DisplayName("페이지 요청")
    inner class Request {
        @Test
        fun `기본값은 첫 페이지 20개다`() {
            val request = PageRequest()

            assertEquals(0, request.page)
            assertEquals(20, request.size)
        }

        @Test
        fun `건너뛸 행 수는 page 곱하기 size다`() {
            assertEquals(40L, PageRequest(page = 2, size = 20).offset)
        }

        @ParameterizedTest
        @CsvSource("-1, 20", "0, 0", "0, 101")
        fun `범위를 벗어난 page나 size는 호출 코드 오류로 거부한다`(
            page: Int,
            size: Int,
        ) {
            assertFailsWith<IllegalArgumentException> { PageRequest(page, size) }
        }

        @ParameterizedTest
        @CsvSource("0, 1", "0, 100")
        fun `size는 1부터 100까지 받는다`(
            page: Int,
            size: Int,
        ) {
            assertEquals(size, PageRequest(page, size).size)
        }
    }

    @Nested
    @DisplayName("전체 페이지 수")
    inner class TotalPages {
        @ParameterizedTest
        @CsvSource("0, 0", "1, 1", "20, 1", "21, 2", "135, 7")
        fun `전체 건수를 size로 나눠 올림한다`(
            totalElements: Long,
            totalPages: Int,
        ) {
            assertEquals(totalPages, Page.of(emptyList<Int>(), PageRequest(size = 20), totalElements).totalPages)
        }
    }

    @Test
    fun `내용을 바꿔도 페이지 정보는 그대로다`() {
        val page = Page.of(listOf(1, 2), PageRequest(page = 3, size = 2), totalElements = 10)

        val mapped = page.withContent(listOf("a", "b"))

        assertEquals(Page(listOf("a", "b"), page = 3, size = 2, totalElements = 10), mapped)
    }
}
