# 영속성

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다. 결정 근거는 [ADR-0009](../adr/0009-flyway-with-exposed-schema-check.md)(Flyway), [ADR-0010](../adr/0010-schema-conventions-and-time.md)(스키마 기본 규칙·시간), [ADR-0011](../adr/0011-transaction-boundary-with-transaction-runner.md)(트랜잭션), [ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md)(Repository 배치), [ADR-0013](../adr/0013-optimistic-locking-for-product-and-option-group.md)(낙관적 잠금)에 있다.

스키마를 어떻게 바꾸고, Exposed로 어떻게 매핑하며, 트랜잭션을 어떻게 여는지 다룬다. 테이블과 컬럼 자체는 [ERD](../erd.md)와 마이그레이션 파일이 원본이다.

## 역할 분담

| 도구 | 역할 | 하지 않는 것 |
|---|---|---|
| Flyway (JDBC) | 스키마 버전 관리와 적용. 앱이 시작될 때 한 번 실행 | 요청 처리 |
| Exposed (R2DBC) | 조회와 변경(DSL) | 테이블 생성(`SchemaUtils.create` 금지) |
| `exposed-migration-r2dbc` | Exposed `Table` 정의와 실제 스키마의 불일치 검사(통합 테스트) | 스키마 적용 |

- Flyway는 `spring-boot-flyway` 모듈만 쓴다. 스타터는 JDBC 연결 풀(HikariCP)을 남기므로 쓰지 않는다. `spring-boot-starter-jdbc`를 추가하거나 요청 경로에서 JDBC를 쓰지 않는다.
- 접속 정보는 설정 파일에 두지 않는다. 로컬은 docker-compose, 테스트는 Testcontainers `@ServiceConnection`이 R2DBC와 JDBC 연결 정보를 함께 만든다. `prod`는 환경 변수로 `spring.r2dbc.*`와 `spring.flyway.*`를 함께 지정한다.

## 마이그레이션 작성 규칙

- 위치와 이름: `src/main/resources/db/migration/V{번호}__{설명}.sql` (예: `V2__add_product_display_name.sql`)
- **적용된 마이그레이션은 고치지 않는다.** 바꿀 일이 생기면 새 버전으로 추가한다. 되돌리기(undo)도 새 마이그레이션으로 한다.
- 스키마를 바꾸는 PR에서 [ERD](../erd.md)와 Exposed `Table` 정의를 함께 고친다. 불일치는 `ExposedSchemaConsistencyTest`가 잡는다.
- 기본 규칙([ADR-0010](../adr/0010-schema-conventions-and-time.md))을 따른다.
  - ID는 identity, 금액은 원 단위 `BIGINT`, 상태값은 `VARCHAR` + `CHECK`
  - 시각은 `TIMESTAMPTZ`, 감사 컬럼 기본값은 `now()`
  - `version`은 낙관적 잠금을 쓰는 `products`, `option_groups`에만 둔다([ADR-0013](../adr/0013-optimistic-locking-for-product-and-option-group.md))
- 상태값을 추가하거나 바꿀 때는 `CHECK` 제약을 교체한다(`ALTER TABLE … DROP CONSTRAINT … , ADD CONSTRAINT …`).
- **큰 테이블 작업은 잠금을 고려한다.** 앱 시작 중에 실행되므로 오래 걸리면 시작이 늦어지고, 다른 인스턴스의 요청이 잠금에 막힐 수 있다.
  - 인덱스는 `CREATE INDEX CONCURRENTLY`로 만든다. 이때는 트랜잭션 밖에서 실행해야 하므로 Flyway의 트랜잭션 비활성 설정을 쓴다.
  - 스키마 변경과 대량 데이터 이전은 나눈다.
- 여러 인스턴스가 동시에 시작해도 Flyway 잠금 테이블이 한 번만 적용되게 한다.

## Exposed 매핑

- `Table` 객체와 row 매핑은 `infrastructure/persistence/<module>/`에 두고, domain 모델과 분리한다([패키지 구조](package-structure.md)).
- 새 `Table`을 만들면 `infrastructure.persistence.ExposedTables.all`에 등록한다. 그래야 불일치 검사 대상이 된다.
- `Table` 정의는 마이그레이션과 같은 이름·옵션으로 쓴다. 다르면 `ExposedSchemaConsistencyTest`가 잡는다.
  - 감사 컬럼은 `auditTimestamp("created_at")`로 만든다. 기본값이 마이그레이션과 같은 `now()`로 표현된다(Exposed 기본 `CurrentTimestampWithTimeZone`은 `CURRENT_TIMESTAMP`로 표현되어 불일치로 보인다).
  - 이름을 붙이지 않은 제약(FK, UNIQUE)은 PostgreSQL이 만든 이름(`<테이블>_<컬럼>_fkey`, `<테이블>_<컬럼>_key`)을 그대로 적는다. FK의 삭제 옵션도 마이그레이션과 맞춘다(지정하지 않았으면 `NO_ACTION`).
  - `CHECK` 제약도 같은 이름으로 정의한다.
- 시각 컬럼은 `timestampWithTimeZone`(`OffsetDateTime`)으로 읽고 매퍼에서 `Instant`로 바꾼다. 금액은 `long`으로 읽어 `Money`로 감싼다.
- 감사 컬럼 `updated_at`은 UPDATE 문에서 `DbNow`(DB 시계)로 채운다.

## Repository 구현 규칙

- 파일 배치: `infrastructure/persistence/<module>/`에 `XxxTable.kt`와 `ExposedXxxRepository.kt`를 둔다. 매핑이 길어지면 매퍼 파일을 따로 둔다.
- **Repository는 자기 애그리거트 테이블만 다룬다**([ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md)). 다른 애그리거트의 데이터가 필요하면 application이 그 애그리거트의 Repository나 조회 포트를 호출한다.
- Repository는 트랜잭션을 열지 않는다. application의 `TransactionRunner.inTransaction` 안에서 호출된다.
- 잠금 조회는 `findByIdForUpdate`처럼 `…ForUpdate` 이름으로 쓴다(`SELECT … FOR UPDATE`). 잠금은 트랜잭션이 끝날 때 풀리므로 반드시 트랜잭션 안에서 부른다.
- 여러 행을 잠그는 조회(예: `ProductRepository.findAllLinkedToForUpdate`)는 교착을 피하도록 항상 id 순서로 잠근다.
- 하위 컬렉션(옵션 목록, 상품의 연결·예외 등)은 저장할 때 지우고 다시 넣는다. 성능 문제가 보이면 그때 차이만 반영하도록 바꾼다.
- 애그리거트 여러 개를 불러올 때 하위 컬렉션은 애그리거트마다 조회하지 않고 테이블마다 한 번(`product_id IN (…)`)씩 조회해 묶는다.
- 낙관적 잠금 대상(`VersionedAggregateRoot`)은 `UPDATE … WHERE id = ? AND version = ?`로 저장하고, 바뀐 행이 0개면 `VersionConflictException`을 던진다. 성공하면 도메인 객체의 `version`을 1 올린다([ADR-0013](../adr/0013-optimistic-locking-for-product-and-option-group.md)).
- Repository는 도메인 이벤트를 발행하지 않는다. application이 저장한 뒤 `pullDomainEvents()`로 꺼내 처리한다.
- 삭제 제한(참조 중인 카테고리 등)은 DB FK로도 막지만, 사용자에게 이유를 알려 주기 위해 application이 먼저 확인한다.
- 통합 테스트는 `IntegrationTest`를 상속하고 다음을 확인한다: 저장 후 조회(왕복), DB 제약(UNIQUE, FK, CHECK), 해당하는 동시성 규칙([ERD](../erd.md#동시성-처리)). 준비 데이터는 검증 대상이 아닌 테이블이면 `execute(sql)`로 직접 넣는다.

## 트랜잭션

- application 서비스는 `TransactionRunner.inTransaction { … }`으로 유스케이스의 트랜잭션 경계를 연다. Repository는 이 트랜잭션 안에서 호출된다.
- 블록이 예외로 끝나면 블록 안의 변경이 모두 롤백된다. 안쪽에서 다시 호출하면 바깥 트랜잭션을 이어 쓴다.
- Spring `@Transactional`과 섞지 않는다.
- 잠금 조회는 Exposed `Query.forUpdate()`를 쓴다. 배치의 `SKIP LOCKED`는 예약 Repository 구현 때 정한다([ERD 동시성 처리](../erd.md#동시성-처리)).

## 시간

- 코드는 시스템 기본 시간대를 쓰지 않는다. 현재 시각은 `Clock` 빈(UTC)으로 구하고, 업무 날짜는 `BusinessTimeZone`(설정값 `catalog.business-zone`, 기본 `Asia/Seoul`)으로 해석한다.
- SQL에서 날짜를 계산하지 않는다(`CURRENT_DATE`, `now()::date` 금지). 필요한 날짜는 앱이 계산해 파라미터로 넘긴다.
- 서버, JVM, DB는 UTC로 둔다. 테스트 JVM 시간대는 Gradle에서 UTC로 고정한다.
