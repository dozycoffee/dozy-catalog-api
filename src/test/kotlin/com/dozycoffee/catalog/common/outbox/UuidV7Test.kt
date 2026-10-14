package com.dozycoffee.catalog.common.outbox

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("UUID v7")
class UuidV7Test {
    private val random = Random(1)

    @Test
    fun `버전 7과 RFC 변종 비트를 가진다`() {
        val uuid = UuidV7.generate(1_700_000_000_000, random)

        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
    }

    @Test
    fun `앞 48비트가 밀리초 시각이다`() {
        val millis = 1_760_000_000_123
        val uuid = UuidV7.generate(millis, random)

        assertEquals(millis, uuid.mostSignificantBits ushr 16)
        assertTrue(uuid.toString().startsWith(String.format("%08x-%04x", millis ushr 16, millis and 0xFFFF)))
    }

    @Test
    fun `시각이 늦을수록 문자열 순서도 뒤다`() {
        val earlier = UuidV7.generate(1_760_000_000_000, random).toString()
        val later = UuidV7.generate(1_760_000_000_001, random).toString()

        assertTrue(earlier < later)
    }

    @Test
    fun `같은 시각이어도 다른 값이 나온다`() {
        val ids = (1..1000).map { UuidV7.generate(1_760_000_000_000, random) }.toSet()

        assertEquals(1000, ids.size)
    }

    @Test
    fun `48비트를 넘거나 음수인 시각은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { UuidV7.generate(-1, random) }
        assertFailsWith<IllegalArgumentException> { UuidV7.generate(1L shl 48, random) }
    }

    @Test
    fun `기본 인자로도 만들 수 있다`() {
        assertEquals(7, UuidV7.generate().version())
    }
}
