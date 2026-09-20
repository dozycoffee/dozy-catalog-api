package com.dozycoffee.catalog.domain.product.event

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.domain.product.model.ProductId

class ProductDiscontinued(
    val productId: ProductId,
) : DomainEvent()
