package com.dozycoffee.catalog.product.presentation.category.dto

import com.dozycoffee.catalog.product.domain.category.Category
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.TopLevelCategory

// 카테고리(docs/api/product.md 카테고리). parentId가 null이면 대분류, 값이 있으면 그 대분류의 소분류다.
data class CategoryResponse(
    val id: Long,
    val name: String,
    val parentId: Long?,
) {
    companion object {
        fun from(category: Category): CategoryResponse =
            CategoryResponse(
                id = category.id.value,
                name = category.name,
                parentId =
                    when (category) {
                        is TopLevelCategory -> null
                        is ChildCategory -> category.parentId.value
                    },
            )
    }
}
