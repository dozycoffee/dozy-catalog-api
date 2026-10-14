package com.dozycoffee.catalog.product.infrastructure.product

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductOptionGroupLink
import com.dozycoffee.catalog.product.domain.tag.TagId
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.selectAll

// 상품의 하위 데이터 중 판매 범위와 무관한 것을 읽는 공용 코드. 상품 애그리거트를 복원하는 ExposedProductRepository와,
// 판매 범위를 읽지 않는 조회(ExposedProductDisplayQuery)가 함께 쓴다. 상품 수와 상관없이 테이블마다 한 번씩만 조회한다.
internal const val OVERRIDE_PRICE = "PRICE"
internal const val OVERRIDE_EXCLUDE = "EXCLUDE"

internal suspend fun loadTagIds(productIds: List<Long>): Map<Long, List<TagId>> =
    ProductTagsTable
        .selectAll()
        .where { ProductTagsTable.productId inList productIds }
        .toList()
        .groupBy({ it[ProductTagsTable.productId] }, { TagId(it[ProductTagsTable.tagId]) })

// 옵션 그룹 연결을 노출 순서대로, 연결마다 그 상품의 예외(가격·제외)를 담아 돌려준다.
internal suspend fun loadOptionGroupLinks(productIds: List<Long>): Map<Long, List<ProductOptionGroupLink>> {
    val overrides =
        ProductOptionOverridesTable
            .selectAll()
            .where { ProductOptionOverridesTable.productId inList productIds }
            .orderBy(ProductOptionOverridesTable.optionKey)
            .toList()
            .groupBy(
                { it[ProductOptionOverridesTable.productId] to it[ProductOptionOverridesTable.optionGroupId] },
                { it.toOptionOverride() },
            )
    return ProductOptionGroupsTable
        .selectAll()
        .where { ProductOptionGroupsTable.productId inList productIds }
        .orderBy(ProductOptionGroupsTable.displayOrder to SortOrder.ASC, ProductOptionGroupsTable.optionGroupId to SortOrder.ASC)
        .toList()
        .groupBy({ it[ProductOptionGroupsTable.productId] }) { row ->
            val productId = row[ProductOptionGroupsTable.productId]
            val optionGroupId = row[ProductOptionGroupsTable.optionGroupId]
            ProductOptionGroupLink(
                OptionGroupId(optionGroupId),
                displayOrder = row[ProductOptionGroupsTable.displayOrder],
                overrides = overrides[productId to optionGroupId].orEmpty(),
            )
        }
}

private fun ResultRow.toOptionOverride(): OptionOverride {
    val optionKey = OptionKey(this[ProductOptionOverridesTable.optionKey])
    return when (val type = this[ProductOptionOverridesTable.overrideType]) {
        OVERRIDE_PRICE -> OptionOverride.Price(optionKey, Money(checkNotNull(this[ProductOptionOverridesTable.price])))
        OVERRIDE_EXCLUDE -> OptionOverride.Exclude(optionKey)
        else -> error("알 수 없는 옵션 예외 종류: $type")
    }
}
