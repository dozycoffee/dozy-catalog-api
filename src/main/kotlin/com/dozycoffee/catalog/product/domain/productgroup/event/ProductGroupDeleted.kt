package com.dozycoffee.catalog.product.domain.productgroup.event

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId

class ProductGroupDeleted(
    val productGroupId: ProductGroupId,
) : DomainEvent()
