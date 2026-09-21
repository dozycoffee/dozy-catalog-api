package com.dozycoffee.catalog.common.exposed

import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager

// PostgreSQL 트랜잭션 단위 권고 잠금(pg_advisory_xact_lock)의 용도별 네임스페이스.
// 두 정수 키 형태 (네임스페이스, 대상)를 써서 용도가 다르면 대상 ID가 같아도 키가 겹치지 않게 한다.
// 두 정수 키와 bigint 하나짜리 키는 PostgreSQL에서 서로 다른 키 공간이라 섞이지 않는다.
// 새 용도는 여기에 겹치지 않는 번호로 추가한다. 이미 쓰는 번호는 바꾸지 않는다 — 배포 중 옛 인스턴스와 새 인스턴스가
// 서로 다른 키로 잠가 직렬화가 깨진다.
enum class AdvisoryLockNamespace(
    val key: Int,
) {
    // 매장 단위 진열 순서 일괄 변경(요구사항 2.3). 대상은 storeId.
    STORE_DISPLAY_ORDER(1),
}

// 현재 트랜잭션이 끝날 때까지 (namespace, targetId) 잠금을 잡는다. 같은 키를 잡으려는 다른 트랜잭션은 커밋·롤백까지 기다린다.
// 대상 ID는 BIGINT지만 두 정수 키의 두 번째 자리는 int4라 하위 32비트만 쓴다. 하위 32비트가 같은 서로 다른 대상은
// 같은 잠금을 공유하게 되지만, 불필요하게 줄을 설 뿐 결과가 틀리지는 않는다.
suspend fun lockForTransaction(
    namespace: AdvisoryLockNamespace,
    targetId: Long,
) {
    TransactionManager.current().exec(
        "SELECT pg_advisory_xact_lock(${namespace.key}, ${targetId.toInt()})",
        explicitStatementType = StatementType.SELECT,
    )
}
