# 영속성

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다. 결정 근거는 [ADR-0009](../adr/0009-flyway-with-exposed-schema-check.md)(Flyway), [ADR-0010](../adr/0010-schema-conventions-and-time.md)(스키마 기본 규칙·시간), [ADR-0011](../adr/0011-transaction-boundary-with-transaction-runner.md)(트랜잭션)에 있다.

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
  - 시각은 `TIMESTAMPTZ`, 애그리거트 루트에는 `version`, 감사 컬럼 기본값은 `now()`
- 상태값을 추가하거나 바꿀 때는 `CHECK` 제약을 교체한다(`ALTER TABLE … DROP CONSTRAINT … , ADD CONSTRAINT …`).
- **큰 테이블 작업은 잠금을 고려한다.** 앱 시작 중에 실행되므로 오래 걸리면 시작이 늦어지고, 다른 인스턴스의 요청이 잠금에 막힐 수 있다.
  - 인덱스는 `CREATE INDEX CONCURRENTLY`로 만든다. 이때는 트랜잭션 밖에서 실행해야 하므로 Flyway의 트랜잭션 비활성 설정을 쓴다.
  - 스키마 변경과 대량 데이터 이전은 나눈다.
- 여러 인스턴스가 동시에 시작해도 Flyway 잠금 테이블이 한 번만 적용되게 한다.

## Exposed 매핑

- `Table` 객체와 row 매핑은 `infrastructure/persistence/<module>/`에 두고, domain 모델과 분리한다([패키지 구조](package-structure.md)).
- 새 `Table`을 만들면 `infrastructure.persistence.ExposedTables.all`에 등록한다. 그래야 불일치 검사 대상이 된다.
- 시각 컬럼은 `timestampWithTimeZone`(`OffsetDateTime`)으로 읽고 매퍼에서 `Instant`로 바꾼다. 금액은 `long`으로 읽어 `Money`로 감싼다.
- 감사 컬럼 `updated_at`은 UPDATE 문에서 DB 시계로 채운다.

## 트랜잭션

- application 서비스는 `TransactionRunner.inTransaction { … }`으로 유스케이스의 트랜잭션 경계를 연다. Repository는 이 트랜잭션 안에서 호출된다.
- 블록이 예외로 끝나면 블록 안의 변경이 모두 롤백된다. 안쪽에서 다시 호출하면 바깥 트랜잭션을 이어 쓴다.
- Spring `@Transactional`과 섞지 않는다.
- 잠금 조회(`FOR UPDATE`, `SKIP LOCKED`) API는 Repository 구현 때 정한다([ERD 동시성 처리](../erd.md#동시성-처리)).

## 시간

- 코드는 시스템 기본 시간대를 쓰지 않는다. 현재 시각은 `Clock` 빈(UTC)으로 구하고, 업무 날짜는 `BusinessTimeZone`(설정값 `catalog.business-zone`, 기본 `Asia/Seoul`)으로 해석한다.
- SQL에서 날짜를 계산하지 않는다(`CURRENT_DATE`, `now()::date` 금지). 필요한 날짜는 앱이 계산해 파라미터로 넘긴다.
- 서버, JVM, DB는 UTC로 둔다. 테스트 JVM 시간대는 Gradle에서 UTC로 고정한다.
