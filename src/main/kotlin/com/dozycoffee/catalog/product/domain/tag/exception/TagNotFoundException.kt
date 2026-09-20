package com.dozycoffee.catalog.product.domain.tag.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.tag.TagId

class TagNotFoundException(
    tagId: TagId,
) : DomainException(
        errorCode = TagErrorCode.TAG_NOT_FOUND,
        message = "태그를 찾을 수 없습니다: ${tagId.value}",
    )
