package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

class CategoryNotFoundException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.CATEGORY_NOT_FOUND,
        message = "카테고리를 찾을 수 없습니다: ${categoryId.value}",
    )
