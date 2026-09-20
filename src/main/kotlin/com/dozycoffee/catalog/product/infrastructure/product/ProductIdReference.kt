package com.dozycoffee.catalog.product.infrastructure.product

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

// 하위 테이블의 product_id. 상품이 삭제되면 FK CASCADE로 함께 삭제된다.
internal fun Table.productIdReference(fkName: String): Column<Long> =
    long("product_id").references(
        ProductsTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = fkName,
    )
