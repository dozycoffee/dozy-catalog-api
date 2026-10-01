package com.dozycoffee.webprobe

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.core.VersionConflictException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

// 경로 인가와 오류 응답 형식을 확인하려고 실제 API 경로 아래에 두는 테스트 전용 컨트롤러.
// 앱의 컴포넌트 스캔 범위(com.dozycoffee.catalog) 밖에 둬서, 이 컨트롤러가 필요한 테스트만 @Import로 등록한다.
@RestController
class WebProbeController {
    @GetMapping("/api/v1/admin/probe", "/api/v1/internal/probe", "/api/v1/other/probe")
    suspend fun probe(): String = "ok"

    @GetMapping("/api/v1/admin/probe/domain-error/{type}")
    suspend fun domainError(
        @PathVariable type: ErrorType,
    ): String = throw ProbeException(type)

    @GetMapping("/api/v1/admin/probe/version-conflict")
    suspend fun versionConflict(): String = throw VersionConflictException(targetId = 12L, expectedVersion = 3, currentVersion = 5)
}

class ProbeException(
    type: ErrorType,
) : DomainException(ProbeErrorCode(type), "프로브 메시지")

class ProbeErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    override val code: String = "PROBE_FAILED"
}
