package com.dozycoffee.catalog.product.application.product.query

import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId

// 본사관리자의 상품 목록 검색 조건(요구사항 1.12). 지정하지 않은 조건은 거르지 않고, 여러 조건을 함께 주면 모두 만족하는 상품만 남는다.
// 조건의 의미는 ProductSearchCondition과 같다.
data class ProductSearchFilter(
    val ids: Set<ProductId>? = null,
    val keyword: String? = null,
    val categoryId: CategoryId? = null,
    val tagId: TagId? = null,
    val groupId: ProductGroupId? = null,
    val status: ProductStatus? = null,
) {
    init {
        requireIdsWithinLimit(ids)
    }
}

// 판매 상품 검색 조건(요구사항 1.12, 2.1). 상품 그룹은 본사 내부 정보이고 상태는 항상 ACTIVE라 조건에 없다.
data class SellableProductFilter(
    val ids: Set<ProductId>? = null,
    val keyword: String? = null,
    val categoryId: CategoryId? = null,
    val tagId: TagId? = null,
) {
    init {
        requireIdsWithinLimit(ids)
    }
}

// ids는 최대 100개다(docs/api/README.md 목록 조회). 넘는 요청은 presentation이 요청 오류로 먼저 거르므로
// 여기까지 오면 호출 코드의 잘못이다(docs/architecture/exception.md).
private const val MAX_IDS = 100

private fun requireIdsWithinLimit(ids: Set<ProductId>?) {
    require(ids == null || ids.size <= MAX_IDS) { "ids는 최대 ${MAX_IDS}개입니다: ${ids?.size}" }
}
