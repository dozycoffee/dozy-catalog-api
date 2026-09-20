package com.dozycoffee.catalog.domain.productgroup.event

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.domain.productgroup.ProductGroupId

class ProductGroupDeleted(
    val productGroupId: ProductGroupId,
) : DomainEvent()
