package com.dozycoffee.catalog.infrastructure.persistence.tag

import com.dozycoffee.catalog.domain.tag.Tag
import com.dozycoffee.catalog.domain.tag.TagId
import com.dozycoffee.catalog.domain.tag.TagRepository
import com.dozycoffee.catalog.infrastructure.persistence.DbNow
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
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
        TagsTable.update({ TagsTable.id eq tag.id.value }) {
            it[name] = tag.name
            it[updatedAt] = DbNow
        }
        return tag
    }

    override suspend fun delete(id: TagId) {
        TagsTable.deleteWhere { TagsTable.id eq id.value }
    }

    private fun ResultRow.toTag() = Tag(TagId(this[TagsTable.id]), this[TagsTable.name])
}
