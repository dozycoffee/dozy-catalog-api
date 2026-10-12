package com.dozycoffee.catalog.common.observability

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("실행 중인 서버의 버전")
class BuildInfoTest {
    private val sha = "338c22e1f0a94b7c8d2e5a6b9c0d1e2f3a4b5c6d"

    @Test
    fun `릴리스 버전을 받으면 그대로 쓰고 revision은 커밋 SHA 전체다`() {
        val info = BuildInfo.of(revision = sha, releaseVersion = "1.2.3")

        assertEquals(BuildInfo(version = "1.2.3", revision = sha), info)
    }

    @Test
    fun `릴리스 버전이 없으면 sha- 와 revision 앞 7자리를 버전으로 쓴다`() {
        val info = BuildInfo.of(revision = sha, releaseVersion = null)

        assertEquals("sha-338c22e", info.version)
        assertEquals(sha, info.revision)
    }

    @Test
    fun `revision도 없으면 로컬 빌드로 본다`() {
        assertEquals(BuildInfo(version = "local", revision = "unknown"), BuildInfo.of(revision = null, releaseVersion = null))
    }

    @Test
    fun `빈 값과 공백은 없는 것으로 본다`() {
        assertEquals(BuildInfo(version = "local", revision = "unknown"), BuildInfo.of(revision = " ", releaseVersion = ""))
    }

    @Test
    fun `revision 없이 릴리스 버전만 받아도 쓴다`() {
        assertEquals(BuildInfo(version = "1.2.3", revision = "unknown"), BuildInfo.of(revision = null, releaseVersion = "1.2.3"))
    }
}
