package com.dozycoffee.catalog.product.domain.product

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId

interface ProductRepository {
    suspend fun findById(id: ProductId): Product?

    // activate/discontinue 상태 전이와 예약 적용의 검증-쓰기 사이의 레이스를 막기 위해
    // SELECT ... FOR UPDATE로 구현한다.
    suspend fun findByIdForUpdate(id: ProductId): Product?

    // 여러 상품을 한 번에 가져온다. 없는 ID는 빠지고, 순서는 상품 id 순이다. 잠그지 않는 읽기다.
    // 목록 조회가 조회 포트로 고른 ID 페이지를 애그리거트로 바꿀 때 쓴다.
    suspend fun findAllByIds(ids: Collection<ProductId>): List<Product>

    // 이 옵션 그룹을 연결한 상품 전체를 상태와 상관없이 잠그고 가져온다. 옵션 목록을 교체할 때
    // OptionReplacementPolicy.check에 넘기고, 사라진 옵션 키의 예외를 정리해 저장한다.
    suspend fun findAllLinkedToForUpdate(optionGroupId: OptionGroupId): List<Product>

    // 점주의 매장 상품 목록용. 노출 판단 1·2단계(Active, 판매 범위 포함)를 만족하는 상품을 상품 id 순으로 가져온다
    // (요구사항 3장). 판매 범위는 상품 애그리거트의 정보라 상품 테이블만 보고 거를 수 있다(docs/adr/0012).
    suspend fun findAllSellableAt(storeId: StoreId): List<Product>

    // 소분류 삭제·대분류 승격 가드(요구사항 1.6). 상품 테이블만 보므로 여기에 둔다(docs/adr/0012).
    suspend fun existsByCategory(categoryId: CategoryId): Boolean

    // 옵션 그룹 삭제 가드(요구사항 1.9).
    suspend fun existsLinkedTo(optionGroupId: OptionGroupId): Boolean

    // 옵션 그룹 연결은 목록 순서대로 노출 순서를 매겨 함께 저장한다. 상태는 DRAFT, 판매 범위는 ALL이다.
    suspend fun insert(newProduct: Product.NewProduct): Product

    // 읽었을 때의 버전과 같을 때만 저장하고 version을 1 올린다. 그 사이 다른 저장이 있었으면
    // VersionConflictException(docs/adr/0013). 하위 컬렉션(대상 매장, 태그, 그룹, 옵션 그룹 연결, 예외)은
    // 지우고 다시 넣는다.
    suspend fun save(product: Product): Product

    // DRAFT인지는 application이 Product.delete()로 먼저 확인한다. 하위 데이터와 매장 진열 설정·판매 가능 여부는
    // DB FK CASCADE로 함께 삭제된다.
    suspend fun delete(id: ProductId)
}
