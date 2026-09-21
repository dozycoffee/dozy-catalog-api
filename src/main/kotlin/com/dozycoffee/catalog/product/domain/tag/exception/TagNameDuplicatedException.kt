package com.dozycoffee.catalog.product.domain.tag.exception

import com.dozycoffee.catalog.core.DomainException

class TagNameDuplicatedException(
    name: String,
) : DomainException(
        errorCode = TagErrorCode.TAG_NAME_DUPLICATED,
        message = "같은 이름의 태그가 이미 있습니다: $name",
    )
