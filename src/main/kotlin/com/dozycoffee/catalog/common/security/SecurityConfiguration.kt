package com.dozycoffee.catalog.common.security

import com.dozycoffee.auth.starter.DozyAuthProperties
import com.dozycoffee.catalog.common.web.TraceIds
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import reactor.core.publisher.Mono

// 호출자별 경로 인가(docs/api/README.md 경로와 인가, docs/adr/0018).
// 토큰 검증 규칙(디코더, 권한 변환, 401·403 응답)은 dozy-auth 스타터의 빈을 그대로 쓰고, 경로 규칙만 더한다.
// 역할을 컨트롤러마다 @PreAuthorize로 붙이지 않고 경로에서 한 번에 정해, 새 엔드포인트가 인가를 빠뜨리지 않게 한다.
// 스타터는 이 빈이 있으면 자기 필터 체인을 만들지 않으므로 stateless·CSRF·public-paths를 여기서 다시 설정한다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogCorsProperties::class)
class SecurityConfiguration {
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        properties: DozyAuthProperties,
        corsProperties: CatalogCorsProperties,
        jwtDecoder: ReactiveJwtDecoder,
        @Qualifier("dozyJwtAuthenticationConverter")
        jwtAuthenticationConverter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
        entryPoint: ServerAuthenticationEntryPoint,
        accessDeniedHandler: ServerAccessDeniedHandler,
    ): SecurityWebFilterChain =
        http {
            // 사전 요청(OPTIONS)은 인가보다 먼저 CORS 규칙으로 답한다. 토큰 없는 사전 요청이 401이 되지 않게 하기 위해서다.
            cors { configurationSource = adminCorsSource(corsProperties.allowedOrigins) }
            authorizeExchange {
                properties.publicPaths.forEach { authorize(it, permitAll) }
                authorize(ADMIN_PATHS, hasRole(CatalogRoles.ADMIN))
                authorize(INTERNAL_PATHS, hasRole(CatalogRoles.STORE_AGENT))
                // 위 경로 밖은 API가 없다. 토큰만 확인하고 나머지는 라우팅(404)에 맡긴다.
                authorize(anyExchange, authenticated)
            }
            oauth2ResourceServer {
                jwt {
                    this.jwtDecoder = jwtDecoder
                    this.jwtAuthenticationConverter = jwtAuthenticationConverter
                }
                authenticationEntryPoint = entryPoint
            }
            exceptionHandling {
                authenticationEntryPoint = entryPoint
                this.accessDeniedHandler = accessDeniedHandler
            }
            securityContextRepository = NoOpServerSecurityContextRepository.getInstance()
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
        }

    // 브라우저(관리 콘솔)가 부르는 본사 API에만 CORS를 연다. 내부 API는 Store 서비스가 서버에서 부르므로 열지 않는다.
    // 쿠키를 쓰지 않고 Bearer 토큰만 받으므로 credentials는 허용하지 않는다.
    // ETag는 콘솔이 읽어 다음 수정의 If-Match에 넣어야 하므로 응답 헤더로 연다(ADR-0013).
    private fun adminCorsSource(origins: List<String>): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                ADMIN_PATHS,
                CorsConfiguration().apply {
                    allowedOrigins = origins
                    allowedMethods = listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE).map { it.name() }
                    allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.IF_MATCH)
                    exposedHeaders = listOf(HttpHeaders.ETAG, HttpHeaders.LOCATION, TraceIds.HEADER)
                    allowCredentials = false
                    maxAge = PREFLIGHT_MAX_AGE_SECONDS
                },
            )
        }

    private companion object {
        const val ADMIN_PATHS = "/api/v1/admin/**"
        const val INTERNAL_PATHS = "/api/v1/internal/**"
        const val PREFLIGHT_MAX_AGE_SECONDS = 3600L
    }
}

// Catalog의 역할. 토큰의 `catalog:{code}`에서 스타터가 접두사를 떼어 `ROLE_{code}` 권한으로 만든다.
object CatalogRoles {
    // 본사 직원. /api/v1/admin/** 전체.
    const val ADMIN = "admin"

    // Store 서비스의 system client. /api/v1/internal/** 전체. 본사 API는 부를 수 없다.
    const val STORE_AGENT = "store_agent"
}
