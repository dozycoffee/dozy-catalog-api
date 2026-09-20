package com.dozycoffee.catalog.product.application.category.command

import com.dozycoffee.catalog.product.domain.category.CategoryId

// 소분류의 부모 변경과 대분류의 강등을 함께 다룬다. 어느 쪽이든 결과는 그 대분류의 소분류다.
data class ChangeCategoryParentCommand(
    val categoryId: CategoryId,
    val parentId: CategoryId,
)
