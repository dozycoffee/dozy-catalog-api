package com.dozycoffee.catalog.product.infrastructure.product

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import com.dozycoffee.catalog.product.infrastructure.tag.TagsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// 태그가 삭제되면 FK CASCADE로 상품에서도 빠진다(요구사항 1.7).
object ProductTagsTable : Table("product_tags") {
    val productId = productIdReference("product_tags_product_id_fkey")
    val tagId =
        long("tag_id")
            .references(
                TagsTable.id,
                onDelete = ReferenceOption.CASCADE,
                onUpdate = ReferenceOption.NO_ACTION,
                fkName = "product_tags_tag_id_fkey",
            ).index("product_tags_tag_idx")
    val createdAt = auditTimestamp("created_at")

    override val primaryKey = PrimaryKey(productId, tagId)
}
