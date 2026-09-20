package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

class CategoryStillReferencedException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.CATEGORY_STILL_REFERENCED,
        message = "다른 상품이 참조 중인 카테고리는 삭제할 수 없습니다: ${categoryId.value}",
    )
