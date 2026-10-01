package com.dozycoffee.catalog.product.presentation.tag.dto

import com.dozycoffee.catalog.product.domain.tag.Tag

// 태그(docs/api/product.md 태그).
data class TagResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(tag: Tag): TagResponse = TagResponse(tag.id.value, tag.name)
    }
}
