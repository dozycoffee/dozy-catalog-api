package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

class CategoryWithChildrenNotDemotableException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.CATEGORY_WITH_CHILDREN_NOT_DEMOTABLE,
        message = "하위 카테고리가 있는 대분류는 소분류로 바꿀 수 없습니다: ${categoryId.value}",
    )
