package com.dozycoffee.catalog.product.application.port

import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId

// 상품 목록 검색(요구사항 1.12)의 조회 포트. 조건 검색과 페이징은 여러 테이블(태그·그룹 연결, 카테고리, 대상 매장)을
// 함께 봐야 해 Repository에 두지 않는다(docs/architecture/package-structure.md 조회 포트).
// 조건에 맞는 상품 ID의 한 페이지만 돌려준다. 상품 자체는 application이 ProductRepository로 불러와 이 순서대로 합친다.
// 트랜잭션은 열지 않는다 — application의 TransactionRunner 안에서 호출된다.
interface ProductSearchQueryPort {
    suspend fun search(
        condition: ProductSearchCondition,
        pageRequest: PageRequest,
    ): Page<ProductId>
}

// 지정하지 않은(null) 조건은 거르지 않고, 여러 조건을 함께 주면 모두 만족하는 상품만 남는다.
data class ProductSearchCondition(
    // 이 ID의 상품만. 빈 집합이면 아무것도 맞지 않는다.
    val ids: Set<ProductId>? = null,
    // 상품명 부분 일치(대소문자 무시) 또는 SKU 완전 일치. 앞뒤 공백을 떼고 비어 있으면 거르지 않는다.
    val keyword: String? = null,
    // 소분류 또는 대분류. 대분류면 그 아래 소분류를 참조하는 상품이 모두 대상이다(요구사항 1.10, 1.12).
    val categoryId: CategoryId? = null,
    val tagId: TagId? = null,
    val groupId: ProductGroupId? = null,
    val status: ProductStatus? = null,
    // 판매 범위에 이 매장 중 하나라도 포함된 상품만(전체이거나, 한정이면서 대상 매장이 겹침). 빈 집합이면 아무것도 맞지 않는다.
    // 상태는 보지 않는다 — 판매 가능 여부처럼 상태도 함께 거르려면 status를 같이 준다.
    val coveredStoreIds: Set<StoreId>? = null,
    val order: ProductSearchOrder,
)

// 상품 id는 등록 순서대로 커지므로 등록 순서를 id로 정한다.
enum class ProductSearchOrder {
    // 최근 등록한 상품이 먼저(본사 목록).
    NEWEST_FIRST,

    // 먼저 등록한 상품이 먼저(점주의 판매 상품 목록, 본사 등록 순).
    OLDEST_FIRST,
}
