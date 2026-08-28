package com.dozycoffee.catalog.domain.product.event

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.shared.DomainEvent

class ProductDiscontinued(
    val productId: ProductId,
) : DomainEvent()
