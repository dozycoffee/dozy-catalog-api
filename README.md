# Dozy Catalog API

본사가 상품을 정의하고, 가맹점(점주)은 그 위에서 진열(순서/노출)만 커스터마이징하는 단일 Catalog Bounded Context 서비스입니다. 가격은 전 매장 동일가 정책이며, 재고 수량은 이 서비스가 소유하지 않고 별도 재고관리 서비스와 이벤트로 연동합니다.

## 기술 스택

- **언어/런타임**: Kotlin 2.3.21, JVM 21 (Gradle toolchain)
- **프레임워크**: Spring Boot 4.1.0, Spring WebFlux(리액티브), Kotlin Coroutines + Project Reactor
- **영속성**: PostgreSQL 18, R2DBC + [Exposed](https://github.com/JetBrains/Exposed)(DSL 기반, Spring Data R2DBC 리포지토리는 사용하지 않음)
- **보안**: Spring Security
- **모니터링**: Spring Boot Actuator (`/actuator/health`, `/actuator/info`만 노출)
- **직렬화**: Jackson (Jackson 3.x, `tools.jackson` groupId)
- **테스트**: JUnit 5, Testcontainers(`@ServiceConnection`로 R2DBC 연결 자동 구성)
- **코드 스타일**: ktlint (`org.jlleitschuh.gradle.ktlint`)
- **의존성 관리**: Gradle 버전 카탈로그 (`gradle/libs.versions.toml`)

## 사전 준비물

- JDK 21
- Docker (로컬 PostgreSQL 실행 및 통합 테스트용 Testcontainers에 필요)

## 시작하기

```bash
# 1. 환경변수 파일 준비
cp .env.example .env
# .env를 열어 POSTGRES_PASSWORD 등 값을 채워주세요.

# 2. 애플리케이션 실행
./gradlew bootRun
```

프로파일을 지정하지 않으면(local/default) `spring-boot-docker-compose`가 `compose.yaml`의 PostgreSQL 컨테이너를 자동으로 기동하고 연결까지 구성하므로, 별도로 DB 접속 정보를 설정할 필요가 없습니다.

## 테스트

```bash
./gradlew test
```

영속성 코드를 다루는 테스트는 Testcontainers로 PostgreSQL 컨테이너를 직접 띄워 검증합니다 (`compose.yaml`과는 무관하게 동작).

## 코드 스타일 검사

```bash
./gradlew ktlintCheck   # 검사만
./gradlew ktlintFormat  # 자동 포맷
```

`./gradlew build`(CI에서 실행하는 명령)는 컴파일, `ktlintCheck`, `test`를 모두 포함합니다.

## 프로젝트 구조

레이어 우선 구조를 사용합니다: 최상위 패키지는 `domain / application / infrastructure / presentation`이고, 그 아래에 애그리거트별 서브패키지(`product`, `optiongroup`, `category`, ...)를 둡니다. Catalog 서비스 자체가 이미 하나의 배포 단위(BC)이므로 내부 애그리거트 간에는 포트/어댑터로 격리하지 않고 직접 호출하거나 도메인 이벤트로 협력하며, 포트 인터페이스는 Store BC 조회·재고관리 서비스 이벤트·POS 이벤트 발행처럼 실제로 다른 시스템과 통신하는 지점에만 사용합니다.

```
com.dozycoffee.catalog
├── domain/            # 애그리거트, 값 객체, 도메인 이벤트, Repository 인터페이스
├── application/        # 유스케이스, Command/Query, 외부 연동 포트
├── infrastructure/      # Exposed 영속성 구현, 외부 시스템 어댑터(acl/messaging/eventing), 스케줄러, 설정
└── presentation/        # REST 컨트롤러, 요청/응답 DTO
```

## CI

`main` 브랜치로의 push와 PR에서 `.github/workflows/ci.yml`이 `./gradlew build`를 실행합니다.
