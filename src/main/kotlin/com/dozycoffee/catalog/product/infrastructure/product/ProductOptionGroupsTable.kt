package com.dozycoffee.catalog.product.infrastructure.product

import com.dozycoffee.catalog.common.exposed.auditTimestamp
import com.dozycoffee.catalog.product.infrastructure.optiongroup.OptionGroupsTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// 상품-옵션 그룹 연결. 연결한 상품이 있으면 옵션 그룹을 삭제할 수 없다(요구사항 1.9, FK 삭제 옵션 없음).
object ProductOptionGroupsTable : Table("product_option_groups") {
    val productId = productIdReference("product_option_groups_product_id_fkey")
    val optionGroupId =
        long("option_group_id")
            .references(
                OptionGroupsTable.id,
                onDelete = ReferenceOption.NO_ACTION,
                onUpdate = ReferenceOption.NO_ACTION,
                fkName = "product_option_groups_option_group_id_fkey",
            ).index("product_option_groups_option_group_idx")

    // 이 상품 안에서 옵션 그룹 사이의 노출 순서
    val displayOrder = integer("display_order")
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(productId, optionGroupId)
}
