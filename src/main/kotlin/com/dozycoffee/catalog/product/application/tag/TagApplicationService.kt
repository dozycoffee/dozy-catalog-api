package com.dozycoffee.catalog.product.application.tag

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.application.tag.command.RenameTagCommand
import com.dozycoffee.catalog.product.domain.tag.Tag
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import com.dozycoffee.catalog.product.domain.tag.exception.TagNotFoundException
import org.springframework.stereotype.Service

// 태그 관리(요구사항 1.7). 태그 생성은 상품 등록·수정 흐름에서만 일어나므로 여기에는 없다.
@Service
class TagApplicationService(
    private val tagRepository: TagRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun rename(command: RenameTagCommand): Tag =
        transactionRunner.inTransaction {
            val tag = tagRepository.findById(command.tagId) ?: throw TagNotFoundException(command.tagId)
            tag.rename(command.name)
            tagRepository.save(tag)
        }

    // 참조 중이어도 삭제할 수 있다. 참조하던 상품의 태그는 FK CASCADE로 함께 지워진다(요구사항 1.7).
    suspend fun delete(tagId: TagId) {
        transactionRunner.inTransaction {
            tagRepository.findById(tagId) ?: throw TagNotFoundException(tagId)
            tagRepository.delete(tagId)
        }
    }
}
