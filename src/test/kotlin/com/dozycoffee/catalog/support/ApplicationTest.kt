package com.dozycoffee.catalog.support

import com.dozycoffee.catalog.common.TransactionRunner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

// 유스케이스 테스트의 공통 기반(docs/architecture/testing.md).
// 실제 Repository와 DB로 검증하므로 IntegrationTest의 컨테이너·데이터 정리를 그대로 쓴다.
// 유스케이스는 스스로 트랜잭션을 열지만, 결과를 확인하려고 테스트가 Repository를 직접 부를 때는
// 트랜잭션이 필요하다. 그 용도로 tx { … }를 둔다.
@Import(RecordingProductEventPublisherConfiguration::class, FakeStoreExistenceValidatorConfiguration::class)
abstract class ApplicationTest : IntegrationTest() {
    @Autowired
    protected lateinit var transactionRunner: TransactionRunner

    protected suspend fun <T> tx(block: suspend () -> T): T = transactionRunner.inTransaction(block)
}
