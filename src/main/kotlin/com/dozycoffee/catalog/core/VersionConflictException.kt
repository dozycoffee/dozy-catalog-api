package com.dozycoffee.catalog.core

// 낙관적 잠금 충돌. currentVersion은 지금 저장된 버전이며, 저장 시점에 충돌을 발견해 알 수 없으면 null이다.
class VersionConflictException(
    val targetId: Any,
    val expectedVersion: Long,
    val currentVersion: Long?,
) : DomainException(
        errorCode = SharedErrorCode.VERSION_CONFLICT,
        message =
            "다른 변경이 먼저 반영되었습니다: 대상 $targetId, 요청 버전 $expectedVersion" +
                (currentVersion?.let { ", 현재 버전 $it" } ?: ""),
    )
