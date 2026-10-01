package com.dozycoffee.catalog.common.security

import com.dozycoffee.auth.starter.DozyAuthProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import reactor.core.publisher.Mono

// 호출자별 경로 인가(docs/api/README.md 경로와 인가, docs/adr/0018).
// 토큰 검증 규칙(디코더, 권한 변환, 401·403 응답)은 dozy-auth 스타터의 빈을 그대로 쓰고, 경로 규칙만 더한다.
// 역할을 컨트롤러마다 @PreAuthorize로 붙이지 않고 경로에서 한 번에 정해, 새 엔드포인트가 인가를 빠뜨리지 않게 한다.
// 스타터는 이 빈이 있으면 자기 필터 체인을 만들지 않으므로 stateless·CSRF·public-paths를 여기서 다시 설정한다.
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        properties: DozyAuthProperties,
        jwtDecoder: ReactiveJwtDecoder,
        @Qualifier("dozyJwtAuthenticationConverter")
        jwtAuthenticationConverter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
        entryPoint: ServerAuthenticationEntryPoint,
        accessDeniedHandler: ServerAccessDeniedHandler,
    ): SecurityWebFilterChain =
        http {
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

    private companion object {
        const val ADMIN_PATHS = "/api/v1/admin/**"
        const val INTERNAL_PATHS = "/api/v1/internal/**"
    }
}

// Catalog의 역할. 토큰의 `catalog:{code}`에서 스타터가 접두사를 떼어 `ROLE_{code}` 권한으로 만든다.
object CatalogRoles {
    // 본사 직원. /api/v1/admin/** 전체.
    const val ADMIN = "admin"

    // Store 서비스의 system client. /api/v1/internal/** 전체. 본사 API는 부를 수 없다.
    const val STORE_AGENT = "store_agent"
}
