package com.dozycoffee.catalog.common.observability

// 실행 중인 서버의 버전. 릴리스는 이미지를 다시 빌드하지 않고 sha- 이미지에 버전 태그만 붙이므로(docs/adr/0019),
// 빌드할 때 아는 값(revision)과 배포할 때 아는 값(릴리스 버전)을 나눠 받는다.
data class BuildInfo(
    val version: String,
    val revision: String,
) {
    companion object {
        const val UNKNOWN_REVISION = "unknown"
        const val LOCAL_VERSION = "local"
        private const val SHORT_REVISION_LENGTH = 7

        // revision: CI가 이미지를 만들 때 넣은 커밋 SHA. 없으면(로컬 빌드) unknown.
        // releaseVersion: 배포가 알려 주는 릴리스 버전(X.Y.Z). 없으면 sha-{revision 7자리}, revision도 없으면 local.
        // 배포가 알려 주는 값은 검사하지 않으므로 둘이 맞지 않으면 revision이 기준이다.
        fun of(
            revision: String?,
            releaseVersion: String?,
        ): BuildInfo {
            val resolvedRevision = revision?.trim().takeUnless { it.isNullOrEmpty() } ?: UNKNOWN_REVISION
            val resolvedVersion =
                releaseVersion?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: if (resolvedRevision == UNKNOWN_REVISION) {
                        LOCAL_VERSION
                    } else {
                        "sha-${resolvedRevision.take(SHORT_REVISION_LENGTH)}"
                    }
            return BuildInfo(version = resolvedVersion, revision = resolvedRevision)
        }
    }
}
