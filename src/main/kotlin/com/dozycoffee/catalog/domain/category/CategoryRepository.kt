package com.dozycoffee.catalog.domain.category

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

    suspend fun save(category: Category): Category
}
