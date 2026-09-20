package com.dozycoffee.catalog.common.exposed

import com.dozycoffee.catalog.common.TransactionRunner
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.stereotype.Component

// Exposed R2DBC 트랜잭션으로 TransactionRunner를 구현한다. Spring @Transactional과 섞지 않는다(docs/adr/0011).
// 이미 트랜잭션 안에서 다시 호출하면 Exposed가 바깥 트랜잭션을 그대로 이어 쓴다.
@Component
class ExposedTransactionRunner(
    private val database: R2dbcDatabase,
) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = suspendTransaction(database) { block() }
}
