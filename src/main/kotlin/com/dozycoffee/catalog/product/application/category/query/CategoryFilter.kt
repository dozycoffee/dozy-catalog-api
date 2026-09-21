package com.dozycoffee.catalog.product.application.category.query

import com.dozycoffee.catalog.common.paging.requireIdsWithinLimit
import com.dozycoffee.catalog.product.domain.category.CategoryId

// 카테고리 목록 조회 조건(요구사항 1.6). 지정하지 않은 조건은 거르지 않고, 여러 조건을 함께 주면 모두 만족하는 것만 남는다.
// parentId는 그 대분류의 소분류만, topLevelOnly는 대분류만 남긴다.
data class CategoryFilter(
    val ids: Set<CategoryId>? = null,
    val parentId: CategoryId? = null,
    val topLevelOnly: Boolean = false,
) {
    init {
        requireIdsWithinLimit(ids)
    }
}
