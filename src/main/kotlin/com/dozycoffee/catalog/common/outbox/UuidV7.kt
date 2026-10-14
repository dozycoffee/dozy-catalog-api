package com.dozycoffee.catalog.common.outbox

import java.security.SecureRandom
import java.util.UUID
import java.util.random.RandomGenerator

// 이벤트 ID로 쓰는 UUID v7(RFC 9562). 앞 48비트가 밀리초 시각이라 같은 시각대의 ID가 모여 인덱스에 유리하고 시간순으로 정렬된다.
// 구독자의 중복 제거 키로 쓰이는 값이므로 같은 이벤트는 다시 발행해도 같은 값을 쓴다(아웃박스에 저장한 값을 그대로 보낸다).
// JDK 21에는 UUID v7이 없고, 이 한 가지 때문에 의존성을 늘리지 않으려고 직접 만든다.
object UuidV7 {
    private val secureRandom = SecureRandom()

    fun generate(
        epochMillis: Long = System.currentTimeMillis(),
        random: RandomGenerator = secureRandom,
    ): UUID {
        require(epochMillis in 0..MAX_TIMESTAMP) { "UUID v7의 시각은 0 이상 48비트 이하여야 합니다: $epochMillis" }
        // 상위 64비트: 시각 48비트 | 버전 4비트(7) | 임의 12비트
        val high = (epochMillis shl 16) or (VERSION shl 12) or (random.nextLong() and RAND_A_MASK)
        // 하위 64비트: 변종 2비트(10) | 임의 62비트
        val low = (random.nextLong() and RAND_B_MASK) or VARIANT
        return UUID(high, low)
    }

    private const val MAX_TIMESTAMP = (1L shl 48) - 1
    private const val VERSION = 7L
    private const val RAND_A_MASK = 0xFFFL
    private const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    private const val VARIANT = Long.MIN_VALUE // 상위 2비트 10
}
