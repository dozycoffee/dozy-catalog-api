package com.dozycoffee.catalog.product.domain.optiongroup

interface OptionGroupRepository {
    suspend fun findById(id: OptionGroupId): OptionGroup?

    // 목록 조회(요구사항 1.9). 지정한 조건만 AND로 걸고 등록 순(id)으로 돌려준다. ids가 비어 있으면 빈 목록이고, 없는 ID는 빠진다.
    // keyword는 이름의 대소문자 무시 부분 일치이며, 공백뿐이면 거르지 않는다.
    suspend fun findAll(
        ids: Set<OptionGroupId>? = null,
        keyword: String? = null,
    ): List<OptionGroup>

    // replaceOptions 검증-쓰기 사이의 레이스를 막기 위해 SELECT ... FOR UPDATE로 구현한다.
    suspend fun findByIdForUpdate(id: OptionGroupId): OptionGroup?

    suspend fun insert(newOptionGroup: OptionGroup.NewOptionGroup): OptionGroup

    // 읽었을 때의 버전과 같을 때만 저장하고 version을 1 올린다. 그 사이 다른 저장이 있었으면
    // VersionConflictException(docs/adr/0013). 옵션 목록은 지우고 다시 넣는다.
    suspend fun save(optionGroup: OptionGroup): OptionGroup

    // 연결한 상품이 있는지는 application이 ProductRepository로 먼저 확인한다(docs/adr/0012). DB도 FK로 막는다.
    suspend fun delete(id: OptionGroupId)
}
