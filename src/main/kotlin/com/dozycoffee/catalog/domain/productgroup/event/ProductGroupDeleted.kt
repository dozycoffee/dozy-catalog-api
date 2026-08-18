package com.dozycoffee.catalog.domain.productgroup.event

import com.dozycoffee.catalog.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.domain.shared.DomainEvent

class ProductGroupDeleted(
    val productGroupId: ProductGroupId,
) : DomainEvent()
