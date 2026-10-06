# 기술 스택

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

어떤 기술을 왜 골랐는지를 다룬다. 정확한 버전은 `gradle/libs.versions.toml`이 원본이라 여기에는 적지 않는다. 빌드·테스트 설정의 주의사항은 루트 [README](../../README.md#빌드테스트-주의사항)에 있다.

## 언어 / 런타임

| 항목 | 선택 |
|---|---|
| 언어 | Kotlin |
| JVM | 21 (Gradle toolchain) |
| 빌드 | Gradle Kotlin DSL + 버전 카탈로그(`gradle/libs.versions.toml`, `libs.xxx` alias) |

## 프레임워크

| 항목 | 선택 | 비고 |
|---|---|---|
| 애플리케이션 | Spring Boot 4 | |
| 웹 스택 | Spring WebFlux (리액티브, non-blocking) | Servlet/MVC 아님 |
| 동시성 모델 | Kotlin Coroutines + Project Reactor | 컨트롤러·서비스는 `suspend` 함수로 작성 |
| 보안 | `dozy-auth`의 `auth-spring-boot-starter` (Spring Security 리액티브 기반) | 토큰 검증·권한 변환·필터 체인·401·403 응답을 스타터가 제공한다. Catalog는 audience `catalog`, realm `internal`로 설정하고 역할 기반 인가만 작성한다. 라이브러리는 GitHub Packages(`maven.pkg.github.com/dozycoffee/dozy-auth`)에서 받으므로 빌드에 인증이 필요하다. 테스트는 `auth-test`의 `@WithDozyPrincipal`·`DozyTestTokens`를 쓴다. 다른 서비스를 서비스 자격으로 부를 때는 스타터의 system token 클라이언트(`@Qualifier("dozySystemWebClient") WebClient.Builder`, `dozy.auth.client.*`)를 쓴다. 토큰 발급·캐시·재발급은 스타터가 맡는다 |
| 모니터링 | Spring Boot Actuator | `health`, `info`만 웹에 노출. 스타터가 모든 요청에 인증을 요구하므로 `/actuator/health`는 `dozy.auth.public-paths`에 넣어 허용한다(k8s·로드밸런서 프로브용) |

## 영속성

| 항목 | 선택 | 이유 |
|---|---|---|
| DB | PostgreSQL | 부분 UNIQUE 인덱스, CHECK, JSONB, `FOR UPDATE SKIP LOCKED`를 설계에 활용 ([ERD](../erd.md)) |
| ORM | JetBrains Exposed (R2DBC) | DSL 기반. Spring Data R2DBC 리포지토리 추상화는 쓰지 않는다 |
| 전송 계층 | `spring-boot-starter-r2dbc` | Boot가 `spring.r2dbc.*`로 `ConnectionFactory`를 자동 구성하는 용도로만 쓴다. Exposed의 `R2dbcDatabase`가 이를 감싼다 |
| 커넥션 풀 | `r2dbc-pool` | Boot 4의 R2DBC 스타터에 포함되지 않아 따로 둔다. 없으면 트랜잭션마다 연결을 새로 연다 |
| 드라이버 | `org.postgresql:r2dbc-postgresql` | 구 groupId `io.r2dbc`에서 이관됨. Boot BOM의 1.1.1은 결과의 마지막 행이 빠질 수 있는 버그가 있어 1.1.2 이상으로 덮어쓴다(`libs.versions.toml` 참고) |
| 로컬 DB | Docker Compose (`compose.yaml`) | `spring-boot-docker-compose`가 `bootRun` 시 자동 기동·연결 |
| 마이그레이션 | Flyway (`spring-boot-flyway` 모듈, JDBC) | 순수 SQL로 PostgreSQL 기능을 그대로 쓴다. R2DBC를 지원하지 않아 앱 시작 시 JDBC로 한 번 실행한다 ([ADR-0009](../adr/0009-flyway-with-exposed-schema-check.md)) |
| Exposed 추가 모듈 | `exposed-java-time`, `exposed-migration-r2dbc`(테스트) | `TIMESTAMPTZ`·`DATE` 매핑, Table 정의와 스키마 불일치 검사 |
| 시간 | `java.time` (`Instant`, `LocalDate`, `Clock`) | JDK 표준이라 Jackson·Spring·Exposed 지원이 가장 넓다. JVM 전용 서비스라 `kotlinx-datetime`의 멀티플랫폼 이점이 없다 ([ADR-0010](../adr/0010-schema-conventions-and-time.md)) |

- Exposed를 직접 쓰므로 영속성 구현은 `<모듈>/infrastructure/<애그리거트>/`에 Exposed `Table` 객체와 `Exposed<Aggregate>Repository`로 둔다 ([패키지 구조](package-structure.md)).
- `@DataR2dbcTest`는 Spring Data 리포지토리용 슬라이스라 이 조합에서는 쓰지 않는다.

## 직렬화

- **Jackson 3** (`tools.jackson` groupId) 하나로 통일한다. Spring Boot 4의 기본값이라 어차피 뺄 수 없고, kotlinx.serialization을 함께 쓰면 타입마다 두 라이브러리의 애노테이션을 관리해야 하기 때문이다.
- JSONB 컬럼(`scheduled_changes.new_value` 등)은 `exposed-json`(kotlinx.serialization 기반) 대신 **Jackson 기반 커스텀 `ColumnType`**(`JsonbColumnType`)을 쓴다. 사용법은 [영속성](persistence.md#jsonb-컬럼)에 있다.

## 테스트

| 항목 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | JUnit 5 + `kotlin.test` | 도메인 객체가 외부 협력자 없는 순수 객체라 Kotest·MockK 없이 충분하다 |
| 리액티브 | `spring-boot-starter-webflux-test`, `kotlinx-coroutines-test` | |
| 시큐리티 | `dozy-auth`의 `auth-test` | `@WithDozyPrincipal`로 인증된 사용자를 만들고, `DozyTestTokens`로 실제 검증 체인을 거치는 토큰을 만든다. `spring-boot-starter-security-test`를 함께 가져온다 |
| 영속성 통합 | Testcontainers (`spring-boot-testcontainers`의 `@ServiceConnection`) | 로컬 개발의 docker-compose 자동 연결과 같은 방식으로 테스트에서도 R2DBC 연결을 자동 구성한다. 접속 정보를 `application.yaml`에 두지 않는 방침과 맞는다 |

## 배포

| 항목 | 선택 | 이유 |
|---|---|---|
| 실행 이미지 | `Dockerfile` (`eclipse-temurin:21-jre`, 레이어 분리, root 아닌 사용자, `linux/amd64`·`linux/arm64`) | 구성이 파일 하나에 드러나 다른 서비스가 그대로 가져다 쓸 수 있다 ([ADR-0019](../adr/0019-container-image-with-dockerfile-and-tag-release.md)) |
| 레지스트리 | GitHub Container Registry (`ghcr.io/dozycoffee/dozy-catalog-api`) | 라이브러리를 받는 GitHub Packages와 권한 체계가 같다 |
| 릴리스 | main 커밋의 `v{major}.{minor}.{patch}` 태그 → `.github/workflows/release.yml` | 리뷰를 거친 커밋만, 의도한 시점에 버전으로 낸다. 방법은 [README](../../README.md#배포) |

## 개발 편의

- `spring-boot-devtools`: 핫 리로드
- `spring-boot-docker-compose`: 로컬 실행 시 PostgreSQL 자동 기동 (테스트 클래스패스에서는 제외)
- `kapt` + `spring-boot-configuration-processor`: `@ConfigurationProperties` 메타데이터
- ktlint (`org.jlleitschuh.gradle.ktlint`): 코드 스타일, pre-commit 훅
