package com.dozycoffee.catalog.product.application.port

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductOptionGroupLink
import com.dozycoffee.catalog.product.domain.product.Sku
import com.dozycoffee.catalog.product.domain.tag.TagId

// Store 서비스에 주는 표시용 정보(기준 정보, 유효 옵션 구성)의 조회 포트(ADR-0020, #130).
// 이 조회는 판매 범위를 쓰지 않으므로 상품 애그리거트(`ProductRepository`)로 복원하지 않고 필요한 값만 읽는다.
// 애그리거트로 복원하면 한정 판매 상품의 대상 매장 목록(상품당 수천 행)을 쓰지도 않으면서 함께 읽게 된다.
// 트랜잭션은 열지 않는다 — application의 TransactionRunner 안에서 호출된다.
interface ProductDisplayQueryPort {
    // 없는 ID는 빠지고 상품 id 순이다. 상태와 무관하다.
    suspend fun findAll(ids: Set<ProductId>): List<ProductDisplayInfo>

    suspend fun findById(id: ProductId): ProductDisplayInfo?
}

// 표시용 정보를 만드는 데 필요한 상품의 값. 판매 범위, 상태, 설명, 상품 그룹, 버전은 담지 않는다.
data class ProductDisplayInfo(
    val id: ProductId,
    val sku: Sku?,
    val name: String,
    val categoryId: CategoryId,
    val imageUrl: String?,
    val basePrice: Money,
    val tracksInventory: Boolean,
    val tagIds: Set<TagId>,
    // 노출 순서대로. 연결마다 그 상품의 옵션 예외를 담는다.
    val optionGroupLinks: List<ProductOptionGroupLink>,
)
