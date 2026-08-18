package com.dozycoffee.catalog.domain.tag

interface TagRepository {
    suspend fun findById(id: TagId): Tag?

    suspend fun findByName(name: String): Tag?

    // 이름 유일 제약 + 동시 등록 레이스를 INSERT ... ON CONFLICT (name) DO UPDATE
    // 방식의 원자적 upsert로 처리한다. find-then-insert 2단계로 노출하지 않아
    // 그 사이의 레이스 자체가 발생할 여지를 없앤다.
    suspend fun findOrCreateByName(name: String): Tag

    suspend fun save(tag: Tag): Tag

    suspend fun delete(id: TagId)
}
