package com.dozycoffee.catalog.common.paging

// 목록 조회의 페이지 요청(docs/api/README.md 페이징). page는 0부터, size는 1~100이다.
// 범위를 벗어난 값은 presentation이 요청 오류(INVALID_REQUEST)로 먼저 거르므로, 여기까지 오면 호출 코드의 잘못이라
// require로 거부한다(docs/architecture/exception.md).
data class PageRequest(
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다: $page" }
        require(size in 1..MAX_SIZE) { "size는 1 이상 $MAX_SIZE 이하여야 합니다: $size" }
    }

    // 건너뛸 행 수. page × size가 Int를 넘을 수 있어 Long으로 계산한다.
    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}
