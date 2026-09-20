package com.dozycoffee.catalog.product.domain.category

interface CategoryRepository {
    suspend fun findById(id: CategoryId): Category?

    suspend fun findTopLevelById(id: CategoryId): TopLevelCategory?

    // becomeChildOf/insertChild에서 부모 후보를 조회할 때 사용 — 검증과 쓰기 사이의
    // 레이스를 막기 위해 SELECT ... FOR UPDATE로 구현한다.
    suspend fun findTopLevelByIdForUpdate(id: CategoryId): TopLevelCategory?

    suspend fun hasChildren(id: CategoryId): Boolean

    suspend fun insertTopLevel(name: String): TopLevelCategory

    suspend fun insertChild(
        name: String,
        parent: TopLevelCategory,
    ): ChildCategory

    // 대분류↔소분류 전환과 부모 변경도 여기서 저장한다(parent_category_id).
    suspend fun save(category: Category): Category

    // 상품 참조와 하위 카테고리 여부는 application이 먼저 확인한다. 상품 참조는 ProductRepository.existsByCategory로 본다.
    suspend fun delete(id: CategoryId)
}
