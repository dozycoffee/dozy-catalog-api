package com.dozycoffee.catalog.product.presentation.publicapi

import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.presentation.internal.dto.InternalCategoryResponse
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

// 토큰 없이 읽을 수 있는 참조 데이터(docs/api/public.md, ADR-0022). 매장과 무관해 매장 소유 확인이 필요 없는 것만 둔다.
// 응답은 Store용 /internal/categories와 같은 트리다. 인가 규칙은 SecurityConfiguration의 경로 규칙에 있다.
@RestController
@RequestMapping("/api/v1/public")
class PublicCategoryController(
    private val categoryService: CategoryApplicationService,
) {
    // 순서 변경의 반영 지연이 곧 max-age다. 채널과 중간 캐시가 이 시간 동안 다시 묻지 않는다.
    @GetMapping("/categories")
    suspend fun categories(): ResponseEntity<List<InternalCategoryResponse>> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePublic())
            .body(InternalCategoryResponse.treeOf(categoryService.list()))

    private companion object {
        val CACHE_MAX_AGE: Duration = Duration.ofMinutes(10)
    }
}
