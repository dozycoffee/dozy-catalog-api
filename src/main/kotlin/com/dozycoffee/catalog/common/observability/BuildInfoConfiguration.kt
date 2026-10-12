package com.dozycoffee.catalog.common.observability

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val logger = KotlinLogging.logger {}

// 실행 중인 서버의 버전을 Prometheus에서 인스턴스별로 볼 수 있게 한다. 버전은 /actuator/info 같은 HTTP 엔드포인트로
// 열지 않는다. 지표 이름과 태그 규칙은 dozy-auth의 dozy.auth.build.info와 맞춘다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogBuildProperties::class)
class BuildInfoConfiguration {
    @Bean
    fun buildInfo(properties: CatalogBuildProperties): BuildInfo =
        BuildInfo.of(revision = properties.revision, releaseVersion = properties.releaseVersion)

    // 값은 항상 1이고 정보는 태그에 담는다. 버전과 revision은 인스턴스마다 하나라 시계열이 늘어나지 않는다.
    @Bean
    fun buildInfoMeterBinder(buildInfo: BuildInfo): MeterBinder =
        MeterBinder { registry: MeterRegistry ->
            Gauge
                .builder("dozy.catalog.build.info") { 1.0 }
                .description("실행 중인 서버의 버전. 값은 항상 1이고 version, revision 태그로 구분한다")
                .tag("version", buildInfo.version)
                .tag("revision", buildInfo.revision)
                .register(registry)
        }

    @Bean
    fun buildInfoLogger(buildInfo: BuildInfo): ApplicationListener<ApplicationReadyEvent> =
        ApplicationListener {
            logger.info { "Dozy Catalog 서버 버전 ${buildInfo.version}, revision ${buildInfo.revision}" }
        }
}

@ConfigurationProperties(prefix = "catalog.build")
data class CatalogBuildProperties(
    // 커밋 SHA. CI가 이미지를 만들 때 CATALOG_REVISION으로 넣는다.
    val revision: String? = null,
    // 릴리스 버전(X.Y.Z). 배포가 CATALOG_RELEASE_VERSION으로 알려 준다.
    val releaseVersion: String? = null,
)
