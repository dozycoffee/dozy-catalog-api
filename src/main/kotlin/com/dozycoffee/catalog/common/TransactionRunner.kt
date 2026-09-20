package com.dozycoffee.catalog.common

// application 서비스가 트랜잭션 경계를 정하는 유일한 수단(docs/adr/0011).
// application은 Exposed를 모르고 이 인터페이스만 안다. 구현은 infrastructure가 한다.
// 블록 안의 모든 Repository 호출은 같은 트랜잭션에서 실행되고, 블록이 예외로 끝나면 전부 롤백된다.
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
