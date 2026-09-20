package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

class CategoryNotAssignableException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.CATEGORY_NOT_ASSIGNABLE,
        message = "상품에는 소분류만 지정할 수 있습니다. 대분류는 지정할 수 없습니다: ${categoryId.value}",
    )
