package com.dozycoffee.catalog.product.infrastructure.product

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import com.dozycoffee.catalog.product.infrastructure.productgroup.ProductGroupsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// 상품 그룹이 삭제되면 FK CASCADE로 상품에서도 빠진다(요구사항 1.8).
object ProductGroupsMapTable : Table("product_groups_map") {
    val productId = productIdReference("product_groups_map_product_id_fkey")
    val groupId =
        long("group_id")
            .references(
                ProductGroupsTable.id,
                onDelete = ReferenceOption.CASCADE,
                onUpdate = ReferenceOption.NO_ACTION,
                fkName = "product_groups_map_group_id_fkey",
            ).index("product_groups_map_group_idx")
    val createdAt = auditTimestamp("created_at")

    override val primaryKey = PrimaryKey(productId, groupId)
}
