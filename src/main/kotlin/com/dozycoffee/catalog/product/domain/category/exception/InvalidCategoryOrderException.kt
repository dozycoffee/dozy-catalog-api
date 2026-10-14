package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

// 순서는 같은 부모를 가진 카테고리 전체를 정확히 한 번씩 담아야 한다. 일부만 담으면 빠진 카테고리의 순서가 정해지지 않고,
// 화면이 본 뒤 새로 생긴 카테고리가 있으면 그 카테고리를 모르는 채 순서를 정하게 되므로 거부한다.
class InvalidCategoryOrderException(
    parentId: CategoryId?,
    siblings: List<CategoryId>,
    requested: List<CategoryId>,
) : DomainException(
        errorCode = CategoryErrorCode.INVALID_CATEGORY_ORDER,
        message =
            "카테고리 순서는 ${parentId?.let { "대분류 ${it.value}의 소분류" } ?: "대분류"} 전체를 한 번씩 포함해야 합니다: " +
                "현재 ${siblings.map { it.value }}, 요청 ${requested.map { it.value }}",
    )
