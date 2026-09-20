package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.category.CategoryId

// 부모로 지정한 카테고리가 없거나 소분류인 경우. 소분류의 부모는 대분류만 될 수 있다(요구사항 1.6).
class TopLevelCategoryNotFoundException(
    categoryId: CategoryId,
) : DomainException(
        errorCode = CategoryErrorCode.TOP_LEVEL_CATEGORY_NOT_FOUND,
        message = "대분류 카테고리를 찾을 수 없습니다: ${categoryId.value}",
    )
