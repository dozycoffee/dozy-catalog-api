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

# 2. pre-commit 훅 설치 (최초 1회 — .git/hooks는 저장소에 커밋되지 않으므로 클론할 때마다 실행 필요)
./gradlew addKtlintFormatGitPreCommitHook

# 3. 애플리케이션 실행
./gradlew bootRun
```

프로파일을 지정하지 않으면(local/default) `spring-boot-docker-compose`가 `compose.yaml`의 PostgreSQL 컨테이너를 자동으로 기동하고 연결까지 구성하므로, 별도로 DB 접속 정보를 설정할 필요가 없습니다.

## 테스트

```bash
./gradlew test
```

영속성 코드를 다루는 테스트는 Testcontainers로 PostgreSQL 컨테이너를 직접 띄워 검증합니다 (`compose.yaml`과는 무관하게 동작).

### 빌드·테스트 주의사항

`build.gradle.kts`, `gradle/libs.versions.toml`을 수정할 때 알아 둘 것들입니다. `DozyCatalogApiApplicationTests`에서 실제로 검증했습니다.

- **Testcontainers 2.x부터 모듈 아티팩트 이름에 `testcontainers-` 접두어가 붙습니다.** `org.testcontainers:postgresql`이 `org.testcontainers:testcontainers-postgresql`로 바뀌었고, `junit-jupiter`도 마찬가지입니다. 예전 문서의 좌표를 그대로 쓰면 404가 납니다.
- **Testcontainers 버전은 `libs.versions.toml`에 직접 적습니다.** `spring-boot-dependencies`가 `testcontainers-bom`을 import하지만, `io.spring.dependency-management` 플러그인은 이 BOM의 버전을 가져오지 못합니다. Spring Boot를 업그레이드할 때 함께 갱신해야 합니다.
- **`org.testcontainers:testcontainers-r2dbc`가 추가로 필요합니다.** R2DBC `@ServiceConnection`이 참조하는 `R2DBCDatabaseContainer` 클래스는 `testcontainers-postgresql`이 아니라 이 모듈에 있습니다. 없으면 컨텍스트 로딩 때 `ClassNotFoundException`으로 실패합니다.
- **`spring-boot-docker-compose`는 테스트 클래스패스에서 제외합니다.** Spring Boot Gradle 플러그인은 기본적으로 `developmentOnly`를 `testRuntimeClasspath`까지 전파합니다. 그러면 테스트 중에도 `compose.yaml` 컨테이너를 띄우려고 해서 Testcontainers와 역할이 겹치고, CI에서는 `.env`가 없어 `POSTGRES_PASSWORD` 누락으로 실패합니다. `build.gradle.kts`의 `configurations { testRuntimeOnly { exclude(...) } }`로 제외합니다.

## 코드 스타일 검사

```bash
./gradlew ktlintCheck   # 검사만
./gradlew ktlintFormat  # 자동 포맷
```

`./gradlew build`(CI에서 실행하는 명령)는 컴파일, `ktlintCheck`, `test`를 모두 포함합니다.

pre-commit 훅(`./gradlew addKtlintFormatGitPreCommitHook`으로 설치, [시작하기](#시작하기) 참고)이 커밋 시 변경된 Kotlin 파일에 `ktlintFormat`을 자동 적용하고 재스테이징합니다.

## 프로젝트 구조

레이어 우선 구조를 사용합니다: 최상위 패키지는 `domain / application / infrastructure / presentation`이고, 그 아래에 애그리거트별 서브패키지(`product`, `optiongroup`, `category`, ...)를 둡니다. Catalog 서비스 자체가 이미 하나의 배포 단위(BC)이므로 내부 애그리거트 간에는 포트/어댑터로 격리하지 않고 직접 호출하거나 도메인 이벤트로 협력하며, 포트 인터페이스는 Store BC 조회·재고관리 서비스 이벤트·POS 이벤트 발행처럼 실제로 다른 시스템과 통신하는 지점에만 사용합니다.

```
com.dozycoffee.catalog
├── domain/            # 애그리거트, 값 객체, 도메인 이벤트, Repository 인터페이스
├── application/        # 유스케이스, Command/Query, 외부 연동 포트
├── infrastructure/      # Exposed 영속성 구현, 외부 시스템 어댑터(acl/messaging/eventing), 스케줄러, 설정
└── presentation/        # REST 컨트롤러, 요청/응답 DTO
```

자세한 원칙과 전체 패키지 트리는 [docs/architecture/package-structure.md](docs/architecture/package-structure.md)를 참고하세요.

## 문서

도메인 요구사항, 시나리오, 도메인 모델, ERD, 아키텍처 문서는 [`docs/`](docs/README.md)에 있습니다. 명세의 원본은 이 저장소이며, 설계를 바꾸는 PR에서는 관련 문서도 함께 수정합니다.

## CI

`main` 브랜치로의 push와 PR에서 `.github/workflows/ci.yml`이 `./gradlew build`를 실행합니다.
