package com.dozycoffee.catalog.domain.tag.event

import com.dozycoffee.catalog.domain.shared.DomainEvent
import com.dozycoffee.catalog.domain.tag.TagId

class TagDeleted(
    val tagId: TagId,
) : DomainEvent()
