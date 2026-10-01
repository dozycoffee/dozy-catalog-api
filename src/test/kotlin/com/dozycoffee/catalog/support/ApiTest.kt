package com.dozycoffee.catalog.support

import com.dozycoffee.auth.test.WithDozyPrincipal
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

// API 테스트의 공통 기반(docs/architecture/testing.md). 유스케이스 테스트처럼 실제 DB까지 거치고, 요청은 WebTestClient로 보낸다.
// 기본 요청자는 본사 직원(catalog:admin)이다. 내부 API 테스트는 클래스에 Store 서비스로 바꿔 붙인다.
//   @WithDozyPrincipal(type = PrincipalType.SYSTEM, roles = ["catalog:store_agent"])
// 토큰 검증 자체를 확인하는 테스트(AuthorizationTest)는 이 클래스를 쓰지 않고 실제 토큰(DozyTestTokens)을 보낸다.
@AutoConfigureWebTestClient
@WithDozyPrincipal(roles = ["catalog:admin"])
abstract class ApiTest : ApplicationTest() {
    @Autowired
    protected lateinit var client: WebTestClient
}
