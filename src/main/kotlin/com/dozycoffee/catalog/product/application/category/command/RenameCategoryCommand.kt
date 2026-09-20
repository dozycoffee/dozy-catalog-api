package com.dozycoffee.catalog.product.application.category.command

import com.dozycoffee.catalog.product.domain.category.CategoryId

data class RenameCategoryCommand(
    val categoryId: CategoryId,
    val name: String,
)
