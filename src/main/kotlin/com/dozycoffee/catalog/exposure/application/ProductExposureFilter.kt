package com.dozycoffee.catalog.exposure.application

import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId

// 노출 현황 조회의 필터(요구사항 1.10). 지정하지 않은 조건은 거르지 않고, 여러 조건을 함께 주면 모두 만족하는 상품만 남는다.
// categoryId는 상품이 참조하는 소분류뿐 아니라 대분류로도 줄 수 있다. 대분류를 주면 그 아래 소분류에 속한 상품이 모두 대상이다.
data class ProductExposureFilter(
    val categoryId: CategoryId? = null,
    val tagId: TagId? = null,
    val groupId: ProductGroupId? = null,
)
