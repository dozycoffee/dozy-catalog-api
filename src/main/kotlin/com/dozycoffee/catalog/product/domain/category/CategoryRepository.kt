package com.dozycoffee.catalog.product.domain.category

interface CategoryRepository {
    suspend fun findById(id: CategoryId): Category?

    suspend fun findTopLevelById(id: CategoryId): TopLevelCategory?

    // becomeChildOf/insertChild에서 부모 후보를 조회할 때 사용 — 검증과 쓰기 사이의
    // 레이스를 막기 위해 SELECT ... FOR UPDATE로 구현한다.
    suspend fun findTopLevelByIdForUpdate(id: CategoryId): TopLevelCategory?

    // 목록 조회(요구사항 1.6). 지정한 조건만 AND로 걸고 등록 순(id)으로 돌려준다. ids가 비어 있으면 빈 목록이고, 없는 ID는 빠진다.
    // parentId는 그 대분류의 소분류만, topLevelOnly는 대분류만 남긴다.
    suspend fun findAll(
        ids: Set<CategoryId>? = null,
        parentId: CategoryId? = null,
        topLevelOnly: Boolean = false,
    ): List<Category>

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
