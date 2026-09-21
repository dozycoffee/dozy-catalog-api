package com.dozycoffee.catalog.product.domain.tag

interface TagRepository {
    suspend fun findById(id: TagId): Tag?

    suspend fun findByName(name: String): Tag?

    // 같은 이름이면 기존 태그를 재사용한다(요구사항 1.7). INSERT … ON CONFLICT (name) DO NOTHING 뒤 조회해,
    // 두 요청이 동시에 같은 이름을 만들어도 태그는 하나만 생긴다. 조회 후 생성으로 나누지 않는다.
    suspend fun findOrCreateByName(name: String): Tag

    // 다른 태그가 이미 쓰는 이름이면 TagNameDuplicatedException. 이 예외 뒤에는 트랜잭션을 이어 쓸 수 없다.
    suspend fun save(tag: Tag): Tag

    // 상품과의 연결(product_tags)은 FK CASCADE로 함께 삭제된다(요구사항 1.7).
    suspend fun delete(id: TagId)
}
