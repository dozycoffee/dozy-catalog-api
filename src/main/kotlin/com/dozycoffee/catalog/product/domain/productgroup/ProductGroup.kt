package com.dozycoffee.catalog.product.domain.productgroup

import com.dozycoffee.catalog.core.AggregateRoot

class ProductGroup internal constructor(
    id: ProductGroupId,
    name: String,
) : AggregateRoot<ProductGroupId>(id) {
    var name: String = name
        private set

    fun rename(newName: String) {
        this.name = newName
    }
}
