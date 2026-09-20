package com.dozycoffee.catalog.support

import com.dozycoffee.catalog.infrastructure.persistence.category.CategoriesTable
import com.dozycoffee.catalog.infrastructure.persistence.optiongroup.OptionGroupsTable
import com.dozycoffee.catalog.infrastructure.persistence.optiongroup.OptionsTable
import com.dozycoffee.catalog.infrastructure.persistence.product.ProductGroupsMapTable
import com.dozycoffee.catalog.infrastructure.persistence.product.ProductOptionGroupsTable
import com.dozycoffee.catalog.infrastructure.persistence.product.ProductOptionOverridesTable
import com.dozycoffee.catalog.infrastructure.persistence.product.ProductTagsTable
import com.dozycoffee.catalog.infrastructure.persistence.product.ProductTargetStoresTable
import com.dozycoffee.catalog.infrastructure.persistence.product.ProductsTable
import com.dozycoffee.catalog.infrastructure.persistence.productgroup.ProductGroupsTable
import com.dozycoffee.catalog.infrastructure.persistence.tag.TagsTable
import com.dozycoffee.catalog.schedule.infrastructure.ScheduledChangesTable
import com.dozycoffee.catalog.store.infrastructure.availability.StoreProductAvailabilitiesTable
import com.dozycoffee.catalog.store.infrastructure.display.StoreDisplaySettingsTable
import org.jetbrains.exposed.v1.core.Table

// Flyway 스키마와 일치해야 하는 Exposed Table 목록. 애그리거트별 Repository를 구현하면서 Table 객체를 만들 때마다
// 여기에 추가한다. 통합 테스트(ExposedSchemaConsistencyTest)가 이 목록과 실제 스키마의 차이를 검사한다.
object ExposedTables {
    val all: List<Table> =
        listOf(
            CategoriesTable,
            TagsTable,
            ProductGroupsTable,
            OptionGroupsTable,
            OptionsTable,
            ProductsTable,
            ProductTargetStoresTable,
            ProductTagsTable,
            ProductGroupsMapTable,
            ProductOptionGroupsTable,
            ProductOptionOverridesTable,
            StoreDisplaySettingsTable,
            StoreProductAvailabilitiesTable,
            ScheduledChangesTable,
        )
}
