package com.dozycoffee.catalog.domain.optiongroup

interface OptionGroupRepository {
    suspend fun findById(id: OptionGroupId): OptionGroup?

    // replaceOptions 검증-쓰기 사이의 레이스를 막기 위해 SELECT ... FOR UPDATE로 구현한다.
    suspend fun findByIdForUpdate(id: OptionGroupId): OptionGroup?

    suspend fun insert(newOptionGroup: OptionGroup.NewOptionGroup): OptionGroup

    suspend fun save(optionGroup: OptionGroup): OptionGroup
}
