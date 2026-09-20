package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

class CategoryHasChildrenException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.CATEGORY_HAS_CHILDREN,
        message = "하위 카테고리가 있는 대분류는 삭제할 수 없습니다: ${categoryId.value}",
    )
