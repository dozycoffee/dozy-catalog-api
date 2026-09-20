package com.dozycoffee.catalog.common.time

import com.dozycoffee.catalog.common.BusinessTimeZone
import com.dozycoffee.catalog.support.IntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals

@DisplayName("시간 설정")
class TimeConfigurationTest : IntegrationTest() {
    @Autowired
    private lateinit var businessTimeZone: BusinessTimeZone

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `업무 기준 시간대는 설정값 Asia Seoul이다`() {
        assertEquals(ZoneId.of("Asia/Seoul"), businessTimeZone.zoneId)
    }

    @Test
    fun `Clock은 UTC 기준이다`() {
        assertEquals(ZoneOffset.UTC, clock.zone)
    }
}
