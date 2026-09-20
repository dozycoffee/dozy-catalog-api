package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

class ReferencedCategoryNotPromotableException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.REFERENCED_CATEGORY_NOT_PROMOTABLE,
        message = "상품이 참조 중인 소분류는 대분류로 바꿀 수 없습니다: ${categoryId.value}",
    )
