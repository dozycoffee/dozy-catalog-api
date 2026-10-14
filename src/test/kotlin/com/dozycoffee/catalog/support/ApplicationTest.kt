package com.dozycoffee.catalog.support

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.common.outbox.OutboxStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// 유스케이스 테스트의 공통 기반(docs/architecture/testing.md).
// 실제 Repository와 DB로 검증하므로 IntegrationTest의 컨테이너·데이터 정리를 그대로 쓴다.
// 유스케이스는 스스로 트랜잭션을 열지만, 결과를 확인하려고 테스트가 Repository를 직접 부를 때는
// 트랜잭션이 필요하다. 그 용도로 tx { … }를 둔다.
@Import(
    FakeStoreAssignabilityCheckerConfiguration::class,
    FakeStoreDirectoryConfiguration::class,
)
abstract class ApplicationTest : IntegrationTest() {
    @Autowired
    protected lateinit var transactionRunner: TransactionRunner

    protected suspend fun <T> tx(block: suspend () -> T): T = transactionRunner.inTransaction(block)

    @Autowired
    private lateinit var outboxStore: OutboxStore

    private val jsonMapper = JsonMapper.builder().build()

    // 유스케이스가 아웃박스에 기록한 이벤트(전송 전)를 id 순서로 읽는다. 상품 변경과 같은 트랜잭션에서 기록되므로
    // 변경이 거부됐으면 비어 있어야 한다. 테스트는 실제 아웃박스 행을 확인하고 가짜 발행자를 쓰지 않는다.
    protected suspend fun outboxEvents(): List<JsonNode> = tx { outboxStore.findPending(1000) }.map { jsonMapper.readTree(it.payload) }

    protected suspend fun clearOutbox() = execute("DELETE FROM outbox_events")
}
