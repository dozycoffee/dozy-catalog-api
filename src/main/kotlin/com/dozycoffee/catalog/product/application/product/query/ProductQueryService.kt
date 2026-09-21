package com.dozycoffee.catalog.product.application.product.query

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.paging.PageRequest
import com.dozycoffee.catalog.product.application.port.ProductSearchCondition
import com.dozycoffee.catalog.product.application.port.ProductSearchOrder
import com.dozycoffee.catalog.product.application.port.ProductSearchQueryPort
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import org.springframework.stereotype.Service

// 본사관리자의 상품 목록 검색(요구사항 1.12). 상태와 관계없이 모든 상품이 대상이고, 최근 등록한 상품이 먼저 온다.
// 조건 검색과 페이징은 조회 포트가, 애그리거트 복원은 ProductRepository가 맡는다.
@Service
class ProductQueryService(
    private val searchQuery: ProductSearchQueryPort,
    private val productRepository: ProductRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun search(
        filter: ProductSearchFilter = ProductSearchFilter(),
        pageRequest: PageRequest = PageRequest(),
    ): Page<Product> =
        transactionRunner.inTransaction {
            val condition =
                ProductSearchCondition(
                    ids = filter.ids,
                    keyword = filter.keyword,
                    categoryId = filter.categoryId,
                    tagId = filter.tagId,
                    groupId = filter.groupId,
                    status = filter.status,
                    order = ProductSearchOrder.NEWEST_FIRST,
                )
            productRepository.loadPage(searchQuery.search(condition, pageRequest))
        }
}

// 조회 포트가 고른 ID 페이지를 같은 순서의 상품 페이지로 바꾼다. 같은 트랜잭션 안에서 부르므로 ID를 고른 뒤 사라진 상품은
// 거의 없지만, 그 사이 삭제됐다면 빠진 채로 돌려준다(전체 건수는 포트가 센 값 그대로다).
internal suspend fun ProductRepository.loadPage(ids: Page<ProductId>): Page<Product> {
    val products = findAllByIds(ids.content).associateBy { it.id }
    return ids.withContent(ids.content.mapNotNull { products[it] })
}
