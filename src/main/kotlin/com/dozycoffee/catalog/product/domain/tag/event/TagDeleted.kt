package com.dozycoffee.catalog.product.domain.tag.event

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.product.domain.tag.TagId

class TagDeleted(
    val tagId: TagId,
) : DomainEvent()
