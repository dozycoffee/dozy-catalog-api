package com.dozycoffee.catalog.common.time

import com.dozycoffee.catalog.common.BusinessTimeZone
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

// 현재 시각은 주입받은 Clock으로만 구한다. 테스트에서 고정된 Clock으로 바꿀 수 있다(docs/adr/0010).
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogTimeProperties::class)
class TimeConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun businessTimeZone(properties: CatalogTimeProperties): BusinessTimeZone =
        object : BusinessTimeZone {
            override val zoneId: ZoneId = properties.businessZone
        }
}

@ConfigurationProperties(prefix = "catalog")
data class CatalogTimeProperties(
    // 업무 기준 시간대. 예약 적용일의 00시를 이 시간대로 해석한다.
    val businessZone: ZoneId = ZoneId.of("Asia/Seoul"),
)
