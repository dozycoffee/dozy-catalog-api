package com.dozycoffee.catalog.product.infrastructure.tag

import com.dozycoffee.catalog.common.exposed.DbNow
import com.dozycoffee.catalog.common.exposed.containsIgnoringCase
import com.dozycoffee.catalog.common.exposed.koreanOrder
import com.dozycoffee.catalog.common.exposed.toSearchKeyword
import com.dozycoffee.catalog.product.domain.tag.Tag
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import com.dozycoffee.catalog.product.domain.tag.exception.TagNameDuplicatedException
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedTagRepository : TagRepository {
    override suspend fun findById(id: TagId): Tag? =
        TagsTable
            .selectAll()
            .where { TagsTable.id eq id.value }
            .firstOrNull()
            ?.toTag()

    override suspend fun findAll(
        ids: Set<TagId>?,
        keyword: String?,
    ): List<Tag> {
        val conditions = mutableListOf<Op<Boolean>>()
        ids?.let { ids -> conditions += if (ids.isEmpty()) Op.FALSE else TagsTable.id inList ids.map { it.value } }
        keyword.toSearchKeyword()?.let { keyword -> conditions += TagsTable.name.containsIgnoringCase(keyword) }
        return TagsTable
            .selectAll()
            .where { conditions.fold(Op.TRUE as Op<Boolean>) { acc, condition -> acc and condition } }
            .orderBy(TagsTable.name.koreanOrder() to SortOrder.ASC, TagsTable.id to SortOrder.ASC)
            .map { it.toTag() }
            .toList()
    }

    override suspend fun findByName(name: String): Tag? =
        TagsTable
            .selectAll()
            .where { TagsTable.name eq name }
            .firstOrNull()
            ?.toTag()

    override suspend fun findOrCreateByName(name: String): Tag {
        TagsTable.insertIgnore { it[TagsTable.name] = name }
        return checkNotNull(findByName(name)) { "태그를 만들었거나 이미 있어야 합니다: $name" }
    }

    override suspend fun save(tag: Tag): Tag {
        try {
            TagsTable.update({ TagsTable.id eq tag.id.value }) {
                it[name] = tag.name
                it[updatedAt] = DbNow
            }
        } catch (e: ExposedR2dbcException) {
            if (e.isNameUniqueViolation()) throw TagNameDuplicatedException(tag.name)
            throw e
        }
        return tag
    }

    override suspend fun delete(id: TagId) {
        TagsTable.deleteWhere { TagsTable.id eq id.value }
    }

    // application이 미리 확인해도 확인과 저장 사이에 다른 요청이 같은 이름을 쓸 수 있다. 그때 DB의 UNIQUE 위반을
    // 사용자가 이유를 알 수 있는 도메인 예외로 바꾼다. Exposed는 드라이버 예외(R2dbcException)를 ExposedR2dbcException으로 감싸 던진다.
    // 제약 이름은 드라이버의 PostgreSQL 전용 타입에만 있어 메시지로 확인한다.
    private fun ExposedR2dbcException.isNameUniqueViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<R2dbcException>()
            .any { it.sqlState == UNIQUE_VIOLATION && it.message.orEmpty().contains(NAME_UNIQUE_CONSTRAINT) }

    private fun ResultRow.toTag() = Tag(TagId(this[TagsTable.id]), this[TagsTable.name])

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val NAME_UNIQUE_CONSTRAINT = "tags_name_key"
    }
}
