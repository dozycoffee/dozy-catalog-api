package com.dozycoffee.webprobe

import com.dozycoffee.catalog.common.paging.Page
import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.common.web.PageResponse
import com.dozycoffee.catalog.common.web.VersionHeaders
import com.dozycoffee.catalog.common.web.toResponse
import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.core.VersionConflictException
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

// 경로 인가와 웹 공통 규약(오류 응답, 버전 헤더, 목록 파라미터, JSON 형식)을 확인하려고 실제 API 경로 아래에 두는 테스트 전용 컨트롤러.
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

    @GetMapping("/api/v1/admin/probe/internal-error")
    suspend fun internalError(): String = throw IllegalStateException("SELECT * FROM secret_table 실패")

    @GetMapping("/api/v1/admin/probe/forbidden-by-method")
    @PreAuthorize("hasRole('nobody')")
    suspend fun forbiddenByMethod(): String = "never"

    @PostMapping("/api/v1/admin/probe/body")
    suspend fun body(
        @RequestBody request: ProbeRequest,
    ): ProbeRequest = request

    @GetMapping("/api/v1/admin/probe/items/{id}")
    suspend fun item(
        @PathVariable id: Long,
    ): Long = id

    @GetMapping("/api/v1/admin/probe/list")
    suspend fun list(
        @RequestParam page: Int?,
        @RequestParam size: Int?,
        @RequestParam ids: String?,
    ): PageResponse<Long> {
        val pageRequest = ListParams.pageRequest(page, size)
        val idSet = ListParams.ids(ids) { it }
        val content = idSet?.sorted() ?: listOf(1L, 2L)
        return Page.of(content, pageRequest, totalElements = content.size.toLong()).toResponse { it }
    }

    @PutMapping("/api/v1/admin/probe/versioned")
    suspend fun versioned(
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
    ): ResponseEntity<ProbeVersioned> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        return VersionHeaders.okWithVersion(ProbeVersioned(version + 1), version + 1)
    }

    @GetMapping("/api/v1/admin/probe/json")
    suspend fun json(): ProbeView =
        ProbeView(
            description = null,
            effectiveDate = LocalDate.parse("2026-10-01"),
            occurredAt = Instant.parse("2026-10-01T00:00:00Z"),
            kind = ProbeKind.FIRST,
            tags = emptyList(),
        )
}

data class ProbeRequest(
    val name: String,
    val count: Int,
    val kind: ProbeKind,
)

enum class ProbeKind { FIRST, SECOND }

data class ProbeVersioned(
    val version: Long,
)

data class ProbeView(
    val description: String?,
    val effectiveDate: LocalDate,
    val occurredAt: Instant,
    val kind: ProbeKind,
    val tags: List<String>,
)

class ProbeException(
    type: ErrorType,
) : DomainException(ProbeErrorCode(type), "프로브 메시지")

class ProbeErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    override val code: String = "PROBE_FAILED"
}
