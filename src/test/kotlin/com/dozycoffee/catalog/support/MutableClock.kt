package com.dozycoffee.catalog.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// 시간이 결과를 바꾸는 유스케이스(예약 등록의 "내일 이후", 배치의 "적용 시각이 지난")를 확인하려고
// 시각을 옮길 수 있게 만든 Clock. 실제 Clock 빈(UTC 시스템 시계) 대신 주입된다.
// 컨텍스트 하나를 여러 테스트가 공유하므로 테스트마다 reset()으로 기준 시각으로 되돌린다.
class MutableClock(
    private var current: Instant = DEFAULT_NOW,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun moveTo(instant: Instant) {
        current = instant
    }

    fun reset() = moveTo(DEFAULT_NOW)

    companion object {
        // 업무 시간대(Asia/Seoul)로 2026-09-21 09:00. 이 시각의 업무 날짜는 2026-09-21이다.
        val DEFAULT_NOW: Instant = Instant.parse("2026-09-21T00:00:00Z")
    }
}

@TestConfiguration
class MutableClockConfiguration {
    @Bean
    @Primary
    fun mutableClock() = MutableClock()
}
