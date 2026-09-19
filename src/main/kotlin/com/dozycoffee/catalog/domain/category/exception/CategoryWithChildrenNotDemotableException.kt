package com.dozycoffee.catalog.domain.category.exception

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.shared.DomainException

class CategoryWithChildrenNotDemotableException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.CATEGORY_WITH_CHILDREN_NOT_DEMOTABLE,
        message = "하위 카테고리가 있는 대분류는 소분류로 바꿀 수 없습니다: ${categoryId.value}",
    )
