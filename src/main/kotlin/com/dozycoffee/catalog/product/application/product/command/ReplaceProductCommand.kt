package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId

// 즉시 반영(PUT). 입력한 값 전체로 상품을 교체한다(요구사항 1.4).
// version은 화면이 보고 있던 버전이다. 그 사이 다른 변경이 반영됐으면 거부한다(ADR-0013).
// 옵션 그룹 연결과 상품별 예외는 전용 유스케이스에서 다룬다(요구사항 1.9).
data class ReplaceProductCommand(
    val productId: ProductId,
    val version: Long,
    val name: String,
    val categoryId: CategoryId,
    val basePrice: Money,
    val description: String? = null,
    val imageUrl: String? = null,
    val tagNames: List<String> = emptyList(),
    val groupIds: Set<ProductGroupId> = emptySet(),
)
