package com.dozycoffee.catalog.common.paging

// 목록 조회의 한 페이지. totalElements는 조건에 맞는 전체 건수라, 마지막 페이지를 지나면 content만 비고 전체 건수는 그대로다.
data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int get() = ((totalElements + size - 1) / size).toInt()

    // 페이지 정보는 그대로 두고 내용만 바꾼다. 조회 포트가 돌려준 ID 페이지를 애그리거트 페이지로 바꿀 때 쓴다.
    fun <R> withContent(content: List<R>): Page<R> = Page(content, page, size, totalElements)

    companion object {
        fun <T> of(
            content: List<T>,
            request: PageRequest,
            totalElements: Long,
        ): Page<T> = Page(content, request.page, request.size, totalElements)

        fun <T> empty(request: PageRequest): Page<T> = of(emptyList(), request, totalElements = 0)
    }
}
