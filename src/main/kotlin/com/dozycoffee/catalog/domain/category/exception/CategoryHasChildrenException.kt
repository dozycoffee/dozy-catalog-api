package com.dozycoffee.catalog.domain.category.exception

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.shared.DomainException

class CategoryHasChildrenException(
    categoryId: CategoryId,
) : DomainException(
        code = "CATEGORY_HAS_CHILDREN",
        message = "하위 카테고리가 있는 대분류는 삭제할 수 없습니다: ${categoryId.value}",
    )
