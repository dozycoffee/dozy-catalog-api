package com.dozycoffee.catalog.application.scheduledchange

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.product.model.OptionOverride
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.domain.scheduledchange.TargetKind
import com.dozycoffee.catalog.domain.shared.Money
import com.dozycoffee.catalog.domain.tag.TagId

// 상품을 대상으로 하는 예약 값. 상품-옵션 그룹 연결의 예약도 상품을 대상으로 한다(docs/adr/0014).
sealed interface ProductFieldValue : ScheduledFieldValue {
    override val targetKind: TargetKind get() = TargetKind.PRODUCT

    data class Name(
        val name: String,
    ) : ProductFieldValue {
        override val fieldName: String get() = "name"
    }

    // 소분류인지는 적용 시점에 application이 다시 확인한다(카테고리가 그 사이 대분류가 될 수 있다).
    data class Category(
        val categoryId: CategoryId,
    ) : ProductFieldValue {
        override val fieldName: String get() = "category"
    }

    data class Description(
        val description: String?,
    ) : ProductFieldValue {
        override val fieldName: String get() = "description"
    }

    data class Image(
        val imageUrl: String?,
    ) : ProductFieldValue {
        override val fieldName: String get() = "image"
    }

    data class BasePrice(
        val basePrice: Money,
    ) : ProductFieldValue {
        override val fieldName: String get() = "basePrice"
    }

    data class Tags(
        val tagIds: Set<TagId>,
    ) : ProductFieldValue {
        override val fieldName: String get() = "tags"
    }

    data class Groups(
        val groupIds: Set<ProductGroupId>,
    ) : ProductFieldValue {
        override val fieldName: String get() = "groups"
    }

    data class Scope(
        val storeScope: StoreScope,
    ) : ProductFieldValue {
        override val fieldName: String get() = "storeScope"
    }

    // 활성화와 단종은 서로 다른 필드라 함께 대기할 수 있다(예: 10/1 활성화 + 10/31 단종).
    // 적용 시점에 상태 전이가 불가능하면 예약은 실패로 기록된다(요구사항 1.3, 1.4).
    data object Activation : ProductFieldValue {
        override val fieldName: String get() = "activation"
    }

    data object Discontinuation : ProductFieldValue {
        override val fieldName: String get() = "discontinuation"
    }

    // 연결할 옵션 그룹 전체와 그 순서. 목록에서 빠진 연결은 그 연결의 예외와 함께 해제된다.
    data class OptionGroupLinks(
        val optionGroupIds: List<OptionGroupId>,
    ) : ProductFieldValue {
        override val fieldName: String get() = "optionGroupLinks"
    }

    // 옵션 그룹 하나에 대한 이 상품의 예외 전체(등록 시점 스냅샷). 옵션 그룹마다 다른 필드라
    // 서로 다른 옵션 그룹의 예외 예약은 함께 대기할 수 있다.
    data class OptionOverrides(
        val optionGroupId: OptionGroupId,
        val overrides: List<OptionOverride>,
    ) : ProductFieldValue {
        override val fieldName: String get() = fieldNameOf(optionGroupId)

        companion object {
            const val FIELD_NAME_PREFIX = "optionOverrides:"

            // 대기 예약을 필드 이름으로 찾을 때(findPendingByTargetForUpdate) 쓴다.
            fun fieldNameOf(optionGroupId: OptionGroupId): String = "$FIELD_NAME_PREFIX${optionGroupId.value}"
        }
    }
}
