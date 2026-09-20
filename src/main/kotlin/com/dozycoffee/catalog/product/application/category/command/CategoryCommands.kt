package com.dozycoffee.catalog.product.application.category.command

import com.dozycoffee.catalog.product.domain.category.CategoryId

// 유스케이스 입력은 도메인 타입으로 바꿔 담는다(문자열 ID를 그대로 넘기지 않는다).
// 필드가 둘 이상인 입력만 Command로 만들고, 식별자 하나만 받는 유스케이스는 파라미터로 받는다.

data class RegisterChildCategoryCommand(
    val parentId: CategoryId,
    val name: String,
)

data class RenameCategoryCommand(
    val categoryId: CategoryId,
    val name: String,
)

data class ChangeCategoryParentCommand(
    val categoryId: CategoryId,
    val parentId: CategoryId,
)
