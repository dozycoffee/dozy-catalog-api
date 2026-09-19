package com.dozycoffee.catalog.infrastructure.persistence.product

import com.dozycoffee.catalog.infrastructure.persistence.auditTimestamp
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or

// 상품별 옵션 예외. 상품-옵션 그룹 연결에 속하므로 연결을 복합 FK로 참조하고, 연결이 해제되면 함께 삭제된다.
// PRICE면 가격이 있고 EXCLUDE면 없다(도메인은 OptionOverride.Price/Exclude로 표현한다).
object ProductOptionOverridesTable : Table("product_option_overrides") {
    val productId = long("product_id")
    val optionGroupId = long("option_group_id")
    val optionKey = varchar("option_key", 64)
    val overrideType = varchar("override_type", 20)
    val price = long("price").nullable()
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(productId, optionGroupId, optionKey)

    init {
        foreignKey(
            productId to ProductOptionGroupsTable.productId,
            optionGroupId to ProductOptionGroupsTable.optionGroupId,
            onUpdate = ReferenceOption.NO_ACTION,
            onDelete = ReferenceOption.CASCADE,
            name = "product_option_overrides_product_id_option_group_id_fkey",
        )
        check("product_option_overrides_override_type_check") { overrideType inList listOf("PRICE", "EXCLUDE") }
        check("product_option_overrides_price_check") { price greaterEq 0L }
        check("product_option_overrides_type_price") {
            ((overrideType eq "PRICE") and price.isNotNull()) or ((overrideType eq "EXCLUDE") and price.isNull())
        }
    }
}
