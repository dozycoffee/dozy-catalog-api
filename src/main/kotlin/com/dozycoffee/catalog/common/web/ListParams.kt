package com.dozycoffee.catalog.common.web

import com.dozycoffee.catalog.common.paging.MAX_IDS
import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest

// 목록 조회의 공통 파라미터(docs/api/README.md 목록 조회). 상한을 넘거나 형식이 틀린 값은 여기서 INVALID_REQUEST로 거부해,
// application 타입(PageRequest, 검색 조건)의 require에 닿지 않게 한다(docs/architecture/package-structure.md).
object ListParams {
    // page는 0부터(기본 0), size는 1~100(기본 20).
    fun pageRequest(
        page: Int?,
        size: Int?,
    ): PageRequest {
        val resolvedPage = page ?: 0
        val resolvedSize = size ?: PageRequest.DEFAULT_SIZE
        if (resolvedPage < 0) throw InvalidRequestException("page는 0 이상이어야 합니다: $resolvedPage")
        if (resolvedSize !in 1..PageRequest.MAX_SIZE) {
            throw InvalidRequestException("size는 1 이상 ${PageRequest.MAX_SIZE} 이하여야 합니다: $resolvedSize")
        }
        return PageRequest(resolvedPage, resolvedSize)
    }

    // 쉼표로 구분한 ID 목록(ids=12,15,20). 주지 않으면 null(거르지 않음)이고, 빈 값(ids=)이면 빈 집합(아무것도 맞지 않음)이다.
    // 모듈의 ID 타입으로 감싸는 일은 wrap이 한다.
    fun <T> ids(
        raw: String?,
        wrap: (Long) -> T,
    ): Set<T>? {
        if (raw == null) return null
        val values = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (values.size > MAX_IDS) throw InvalidRequestException("ids는 최대 ${MAX_IDS}개입니다: ${values.size}")
        return values
            .map { value -> value.toLongOrNull()?.takeIf { it > 0 } ?: throw InvalidRequestException("ids에 ID가 아닌 값이 있습니다: $value") }
            .map(wrap)
            .toSet()
    }
}

// 페이지 응답(docs/api/README.md 페이징).
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

fun <T, R> Page<T>.toResponse(mapper: (T) -> R): PageResponse<R> =
    PageResponse(
        content = content.map(mapper),
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
