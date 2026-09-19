package com.dozycoffee.catalog.domain.optiongroup

interface OptionGroupRepository {
    suspend fun findById(id: OptionGroupId): OptionGroup?

    // replaceOptions 검증-쓰기 사이의 레이스를 막기 위해 SELECT ... FOR UPDATE로 구현한다.
    suspend fun findByIdForUpdate(id: OptionGroupId): OptionGroup?

    suspend fun insert(newOptionGroup: OptionGroup.NewOptionGroup): OptionGroup

    // 읽었을 때의 버전과 같을 때만 저장하고 version을 1 올린다. 그 사이 다른 저장이 있었으면
    // VersionConflictException(docs/adr/0013). 옵션 목록은 지우고 다시 넣는다.
    suspend fun save(optionGroup: OptionGroup): OptionGroup

    // 연결한 상품이 있는지는 application이 ProductRepository로 먼저 확인한다(docs/adr/0012). DB도 FK로 막는다.
    suspend fun delete(id: OptionGroupId)
}
