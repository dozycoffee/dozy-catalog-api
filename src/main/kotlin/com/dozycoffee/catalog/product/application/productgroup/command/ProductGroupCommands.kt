package com.dozycoffee.catalog.product.application.productgroup.command

import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId

data class RenameProductGroupCommand(
    val productGroupId: ProductGroupId,
    val name: String,
)
