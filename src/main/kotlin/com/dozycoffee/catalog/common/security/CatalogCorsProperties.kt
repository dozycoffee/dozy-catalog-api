package com.dozycoffee.catalog.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

// 본사 API를 브라우저에서 부를 수 있는 출처(관리 콘솔). 환경 변수 CATALOG_CORS_ALLOWED_ORIGINS(쉼표 구분)로 받는다.
// dozy-auth와 같이 scheme://host[:port]만 받고 와일드카드는 쓰지 않는다. 형식이 틀리면 기동에 실패한다.
@ConfigurationProperties(prefix = "catalog.cors")
data class CatalogCorsProperties(
    val allowedOrigins: List<String> = emptyList(),
) {
    init {
        allowedOrigins.forEach { origin ->
            require(ORIGIN.matches(origin)) {
                "catalog.cors.allowed-origins는 scheme://host[:port] 형식이어야 합니다(경로·와일드카드 불가): $origin"
            }
        }
    }

    private companion object {
        val ORIGIN = Regex("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$")
    }
}
