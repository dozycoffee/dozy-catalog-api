package com.dozycoffee.catalog.domain.category.exception

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.shared.DomainException

class CategoryStillReferencedException(
    categoryId: CategoryId,
) : DomainException(
        code = "CATEGORY_STILL_REFERENCED",
        message = "다른 상품이 참조 중인 카테고리는 삭제할 수 없습니다: ${categoryId.value}",
    )
