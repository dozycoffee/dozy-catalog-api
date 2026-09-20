package com.dozycoffee.catalog.product.domain.product.event

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.product.domain.product.ProductId

class ProductDiscontinued(
    val productId: ProductId,
) : DomainEvent()
