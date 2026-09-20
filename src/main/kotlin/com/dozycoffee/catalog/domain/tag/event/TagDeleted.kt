package com.dozycoffee.catalog.domain.tag.event

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.domain.tag.TagId

class TagDeleted(
    val tagId: TagId,
) : DomainEvent()
