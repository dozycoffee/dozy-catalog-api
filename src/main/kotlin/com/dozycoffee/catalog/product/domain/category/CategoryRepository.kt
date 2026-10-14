package com.dozycoffee.catalog.product.domain.category

interface CategoryRepository {
    suspend fun findById(id: CategoryId): Category?

    suspend fun findTopLevelById(id: CategoryId): TopLevelCategory?

    // becomeChildOf/insertChild에서 부모 후보를 조회할 때 사용 — 검증과 쓰기 사이의
    // 레이스를 막기 위해 SELECT ... FOR UPDATE로 구현한다.
    suspend fun findTopLevelByIdForUpdate(id: CategoryId): TopLevelCategory?

    // 목록 조회(요구사항 1.6). 지정한 조건만 AND로 걸고 노출 순서로 돌려준다(순서 값, 같으면 id 순).
    // 순서는 같은 부모끼리만 의미가 있어, 섞인 목록에서도 같은 부모끼리의 상대 순서가 노출 순서다. ids가 비어 있으면 빈 목록이고, 없는 ID는 빠진다.
    // parentId는 그 대분류의 소분류만, topLevelOnly는 대분류만 남긴다.
    suspend fun findAll(
        ids: Set<CategoryId>? = null,
        parentId: CategoryId? = null,
        topLevelOnly: Boolean = false,
    ): List<Category>

    suspend fun hasChildren(id: CategoryId): Boolean

    // 순서 변경과 형제 목록 확인 사이에 다른 순서 변경이 끼어들지 않도록 이 형제 묶음(parentId가 null이면 대분류들)을
    // 트랜잭션이 끝날 때까지 잠근다.
    suspend fun lockSiblings(parentId: CategoryId?)

    // 주어진 순서대로 1부터 번호를 매긴다. 같은 부모의 형제 전체인지는 application이 먼저 확인한다.
    suspend fun replaceOrder(orderedIds: List<CategoryId>)

    // 새 카테고리는 같은 형제 맨 뒤에 놓인다.
    suspend fun insertTopLevel(name: String): TopLevelCategory

    suspend fun insertChild(
        name: String,
        parent: TopLevelCategory,
    ): ChildCategory

    // 대분류↔소분류 전환과 부모 변경도 여기서 저장한다(parent_category_id). 부모가 바뀌면 새 형제 맨 뒤로 간다.
    suspend fun save(category: Category): Category

    // 상품 참조와 하위 카테고리 여부는 application이 먼저 확인한다. 상품 참조는 ProductRepository.existsByCategory로 본다.
    suspend fun delete(id: CategoryId)
}
