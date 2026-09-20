package com.dozycoffee.catalog.product.domain.tag

import com.dozycoffee.catalog.core.AggregateRoot

class Tag internal constructor(
    id: TagId,
    name: String,
) : AggregateRoot<TagId>(id) {
    var name: String = name
        private set

    fun rename(newName: String) {
        this.name = newName
    }
}
