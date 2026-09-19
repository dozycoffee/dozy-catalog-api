package com.dozycoffee.catalog.domain.category.exception

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.shared.DomainException

class InvalidParentCategoryException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.INVALID_PARENT_CATEGORY,
        message = "자기 자신을 부모로 지정할 수 없습니다: ${categoryId.value}",
    )
