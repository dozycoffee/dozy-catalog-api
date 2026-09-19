package com.dozycoffee.catalog.domain.shared

// 낙관적 잠금을 쓰는 애그리거트 루트(docs/adr/0013). 관리자가 화면에서 오래 보다가 전체를 덮어쓰는 수정이 있고,
// 관리자와 시스템(예약 배치, 옵션 교체)이 같은 대상을 바꾸는 Product와 OptionGroup만 상속한다.
// version은 Repository만 올린다. 저장할 때 UPDATE … WHERE version = ?로 한 번 더 확인한다.
abstract class VersionedAggregateRoot<ID : Any>(
    id: ID,
    version: Long,
) : AggregateRoot<ID>(id) {
    var version: Long = version
        internal set

    // 화면이 보고 있던 버전(expected)이 지금 버전과 다르면 그 사이 다른 변경이 반영된 것이다.
    // 사람의 요청은 자동으로 재시도하지 않고, 화면이 최신 값을 다시 불러와 판단하게 한다.
    fun checkVersion(expected: Long) {
        if (expected != version) {
            throw VersionConflictException(id, expected, currentVersion = version)
        }
    }
}
