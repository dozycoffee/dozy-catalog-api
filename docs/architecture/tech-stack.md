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
| 보안 | Spring Security (리액티브) | 인증 방식은 미정 ([개요](README.md#미정-사항)) |
| 모니터링 | Spring Boot Actuator | `health`, `info`만 웹에 노출. Security가 있어도 `/actuator/health`는 Boot 기본값으로 인증 없이 허용된다(k8s·로드밸런서 프로브용, 직접 확인함) |

## 영속성

| 항목 | 선택 | 이유 |
|---|---|---|
| DB | PostgreSQL | 부분 UNIQUE 인덱스, CHECK, JSONB, `FOR UPDATE SKIP LOCKED`를 설계에 활용 ([ERD](../erd.md)) |
| ORM | JetBrains Exposed (R2DBC) | DSL 기반. Spring Data R2DBC 리포지토리 추상화는 쓰지 않는다 |
| 전송 계층 | `spring-boot-starter-r2dbc` | Boot가 `spring.r2dbc.*`로 `ConnectionFactory`를 자동 구성하는 용도로만 쓴다. Exposed의 `R2dbcDatabase`가 이를 감싼다 |
| 드라이버 | `org.postgresql:r2dbc-postgresql` | 구 groupId `io.r2dbc`에서 이관됨 |
| 로컬 DB | Docker Compose (`compose.yaml`) | `spring-boot-docker-compose`가 `bootRun` 시 자동 기동·연결 |
| 마이그레이션 | Flyway (`spring-boot-flyway` 모듈, JDBC) | 순수 SQL로 PostgreSQL 기능을 그대로 쓴다. R2DBC를 지원하지 않아 앱 시작 시 JDBC로 한 번 실행한다 ([ADR-0009](../adr/0009-flyway-with-exposed-schema-check.md)) |
| Exposed 추가 모듈 | `exposed-java-time`, `exposed-migration-r2dbc`(테스트) | `TIMESTAMPTZ`·`DATE` 매핑, Table 정의와 스키마 불일치 검사 |
| 시간 | `java.time` (`Instant`, `LocalDate`, `Clock`) | JDK 표준이라 Jackson·Spring·Exposed 지원이 가장 넓다. JVM 전용 서비스라 `kotlinx-datetime`의 멀티플랫폼 이점이 없다 ([ADR-0010](../adr/0010-schema-conventions-and-time.md)) |

- Exposed를 직접 쓰므로 영속성 구현은 `infrastructure/persistence/<module>/`에 Exposed `Table` 객체와 `Exposed<Module>Repository`로 둔다 ([패키지 구조](package-structure.md)).
- `@DataR2dbcTest`는 Spring Data 리포지토리용 슬라이스라 이 조합에서는 쓰지 않는다.

## 직렬화

- **Jackson 3** (`tools.jackson` groupId) 하나로 통일한다. Spring Boot 4의 기본값이라 어차피 뺄 수 없고, kotlinx.serialization을 함께 쓰면 타입마다 두 라이브러리의 애노테이션을 관리해야 하기 때문이다.
- JSONB 컬럼(`scheduled_changes.new_value` 등)은 `exposed-json`(kotlinx.serialization 기반) 대신 **Jackson 기반 커스텀 `ColumnType`**을 직접 구현한다. 영속성 구현 시점에 만든다(아직 코드 없음).

## 테스트

| 항목 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | JUnit 5 + `kotlin.test` | 도메인 객체가 외부 협력자 없는 순수 객체라 Kotest·MockK 없이 충분하다 |
| 리액티브 | `spring-boot-starter-webflux-test`, `kotlinx-coroutines-test` | |
| 시큐리티 | `spring-boot-starter-security-test` | |
| 영속성 통합 | Testcontainers (`spring-boot-testcontainers`의 `@ServiceConnection`) | 로컬 개발의 docker-compose 자동 연결과 같은 방식으로 테스트에서도 R2DBC 연결을 자동 구성한다. 접속 정보를 `application.yaml`에 두지 않는 방침과 맞는다 |

## 개발 편의

- `spring-boot-devtools`: 핫 리로드
- `spring-boot-docker-compose`: 로컬 실행 시 PostgreSQL 자동 기동 (테스트 클래스패스에서는 제외)
- `kapt` + `spring-boot-configuration-processor`: `@ConfigurationProperties` 메타데이터
- ktlint (`org.jlleitschuh.gradle.ktlint`): 코드 스타일, pre-commit 훅
