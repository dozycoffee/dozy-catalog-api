package com.dozycoffee.catalog.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

// 브라우저가 Catalog를 부를 수 있는 출처. dozy-auth와 같이 scheme://host[:port]만 받고 와일드카드는 쓰지 않는다. 형식이 틀리면 기동에 실패한다.
// - allowedOrigins: 본사 API(관리 콘솔). 환경 변수 CATALOG_CORS_ALLOWED_ORIGINS(쉼표 구분).
// - publicAllowedOrigins: 공개 경로(/api/v1/public/**)를 직접 읽는 웹 채널. 환경 변수 CATALOG_PUBLIC_CORS_ALLOWED_ORIGINS(쉼표 구분).
//   본사 API의 출처와 섞이면 서로의 경로에 출처가 열리므로 따로 받는다. 비어 있으면 어떤 출처도 허용하지 않는다.
@ConfigurationProperties(prefix = "catalog.cors")
data class CatalogCorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val publicAllowedOrigins: List<String> = emptyList(),
) {
    init {
        allowedOrigins.forEach { requireOrigin("catalog.cors.allowed-origins", it) }
        publicAllowedOrigins.forEach { requireOrigin("catalog.cors.public-allowed-origins", it) }
    }

    private fun requireOrigin(
        property: String,
        origin: String,
    ) {
        require(ORIGIN.matches(origin)) { "${property}는 scheme://host[:port] 형식이어야 합니다(경로·와일드카드 불가): $origin" }
    }

    private companion object {
        val ORIGIN = Regex("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$")
    }
}
