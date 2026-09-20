package com.dozycoffee.catalog.product.application.tag.command

import com.dozycoffee.catalog.product.domain.tag.TagId

data class RenameTagCommand(
    val tagId: TagId,
    val name: String,
)
