package com.dozycoffee.catalog.exposure.application

import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId

// 노출 현황 조회의 필터(요구사항 1.10). 지정하지 않은 조건은 거르지 않고, 여러 조건을 함께 주면 모두 만족하는 상품만 남는다.
// ids는 상품 ID로 거른다. 빈 집합이면 어떤 상품도 남지 않는다.
// categoryId는 상품이 참조하는 소분류뿐 아니라 대분류로도 줄 수 있다. 대분류를 주면 그 아래 소분류에 속한 상품이 모두 대상이다.
data class ProductExposureFilter(
    val ids: Set<ProductId>? = null,
    val categoryId: CategoryId? = null,
    val tagId: TagId? = null,
    val groupId: ProductGroupId? = null,
) {
    init {
        // ids는 최대 100개다(docs/api/README.md 목록 조회). 넘는 요청은 presentation이 요청 오류로 먼저 거르므로
        // 여기까지 오면 호출 코드의 잘못이다(docs/architecture/exception.md).
        require(ids == null || ids.size <= MAX_IDS) { "ids는 최대 ${MAX_IDS}개입니다: ${ids?.size}" }
    }

    private companion object {
        const val MAX_IDS = 100
    }
}
