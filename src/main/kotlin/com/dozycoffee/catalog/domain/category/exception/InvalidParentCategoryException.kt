package com.dozycoffee.catalog.domain.category.exception

import com.dozycoffee.catalog.domain.shared.DomainException

class InvalidParentCategoryException(
    message: String,
) : DomainException(
        code = "INVALID_PARENT_CATEGORY",
        message = message,
    )
