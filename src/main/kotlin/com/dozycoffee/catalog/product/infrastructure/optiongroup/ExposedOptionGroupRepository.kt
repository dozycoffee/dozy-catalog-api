package com.dozycoffee.catalog.product.infrastructure.optiongroup

import com.dozycoffee.catalog.common.exposed.DbNow
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.VersionConflictException
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedOptionGroupRepository : OptionGroupRepository {
    override suspend fun findById(id: OptionGroupId): OptionGroup? = selectRoot(id).firstOrNull()?.toOptionGroup()

    // 옵션 목록 교체의 검증과 쓰기 사이에 다른 변경이 끼어들지 않도록 루트 행을 잠근다(ERD 동시성 처리).
    override suspend fun findByIdForUpdate(id: OptionGroupId): OptionGroup? = selectRoot(id).forUpdate().firstOrNull()?.toOptionGroup()

    override suspend fun insert(newOptionGroup: OptionGroup.NewOptionGroup): OptionGroup {
        val id =
            OptionGroupsTable.insert {
                it[name] = newOptionGroup.name
                it[selectionType] = newOptionGroup.selectionType.name
                it[required] = newOptionGroup.required
            }[OptionGroupsTable.id]
        insertOptions(id, newOptionGroup.options)
        return OptionGroup(
            id = OptionGroupId(id),
            name = newOptionGroup.name,
            selectionType = newOptionGroup.selectionType,
            required = newOptionGroup.required,
            options = newOptionGroup.options,
            version = 0,
        )
    }

    // 루트는 읽었을 때의 버전과 같을 때만 저장한다(docs/adr/0013). 바뀐 행이 없으면 그 사이 다른 트랜잭션이
    // 저장한 것이므로 옵션 목록도 건드리지 않고 충돌로 거부한다.
    override suspend fun save(optionGroup: OptionGroup): OptionGroup {
        val id = optionGroup.id.value
        val updated =
            OptionGroupsTable.update({
                (OptionGroupsTable.id eq id) and (OptionGroupsTable.version eq optionGroup.version)
            }) {
                it[name] = optionGroup.name
                it[selectionType] = optionGroup.selectionType.name
                it[required] = optionGroup.required
                it[version] = OptionGroupsTable.version + 1
                it[updatedAt] = DbNow
            }
        if (updated == 0) {
            throw VersionConflictException(optionGroup.id, optionGroup.version, currentVersion = null)
        }
        OptionsTable.deleteWhere { OptionsTable.optionGroupId eq id }
        insertOptions(id, optionGroup.options)
        optionGroup.version += 1
        return optionGroup
    }

    // 연결한 상품이 있으면 DB FK(product_option_groups)가 거부한다. 사용자에게 이유를 알려 주는 확인은
    // application이 ProductRepository로 먼저 한다(docs/adr/0012). 옵션은 FK CASCADE로 함께 삭제된다.
    override suspend fun delete(id: OptionGroupId) {
        OptionGroupsTable.deleteWhere { OptionGroupsTable.id eq id.value }
    }

    private fun selectRoot(id: OptionGroupId): Query = OptionGroupsTable.selectAll().where { OptionGroupsTable.id eq id.value }

    // 목록 순서를 display_order로 저장한다.
    private suspend fun insertOptions(
        optionGroupId: Long,
        options: List<Option>,
    ) {
        OptionsTable.batchInsert(options.withIndex(), shouldReturnGeneratedValues = false) { (index, option) ->
            this[OptionsTable.optionGroupId] = optionGroupId
            this[OptionsTable.optionKey] = option.optionKey.value
            this[OptionsTable.name] = option.name
            this[OptionsTable.price] = option.price.amount
            this[OptionsTable.displayOrder] = index
        }
    }

    private suspend fun findOptions(optionGroupId: Long): List<Option> =
        OptionsTable
            .selectAll()
            .where { OptionsTable.optionGroupId eq optionGroupId }
            .orderBy(OptionsTable.displayOrder)
            .map { it.toOption() }
            .toList()

    private suspend fun ResultRow.toOptionGroup(): OptionGroup {
        val id = this[OptionGroupsTable.id]
        return OptionGroup(
            id = OptionGroupId(id),
            name = this[OptionGroupsTable.name],
            selectionType = SelectionType.valueOf(this[OptionGroupsTable.selectionType]),
            required = this[OptionGroupsTable.required],
            options = findOptions(id),
            version = this[OptionGroupsTable.version],
        )
    }

    private fun ResultRow.toOption() =
        Option(
            optionKey = OptionKey(this[OptionsTable.optionKey]),
            name = this[OptionsTable.name],
            price = Money(this[OptionsTable.price]),
        )
}
