package com.dozycoffee.catalog.product.application.category.command

import com.dozycoffee.catalog.product.domain.category.CategoryId

// 소분류 등록. 부모는 대분류여야 하며, application이 잠근 뒤 확인한다.
data class RegisterChildCategoryCommand(
    val parentId: CategoryId,
    val name: String,
)
