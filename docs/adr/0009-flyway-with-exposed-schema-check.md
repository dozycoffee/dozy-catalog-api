# ADR-0009: 스키마 마이그레이션은 Flyway(JDBC)로 하고, Exposed로 스키마 불일치를 검사한다

- **상태**: 채택
- **날짜**: 2026-09-19
- **관련**: 이슈 #45, [영속성 규칙](../architecture/persistence.md), [ERD](../erd.md)

## 맥락

영속성 계층을 만들려면 스키마의 버전을 관리하고 적용할 도구가 필요했다. 앱은 WebFlux와 R2DBC, Exposed로 동작하고, ERD는 PostgreSQL 전용 기능(부분 UNIQUE 인덱스, CHECK, JSONB)에 기대고 있다. 한편 Flyway는 R2DBC를 지원하지 않는다. flyway/flyway#2502가 2019년부터 열려 있지만 2026-09 기준 최신 버전(13.x)에도 들어가지 않았다.

## 결정

- **Flyway로 스키마를 관리하고 적용한다.** 마이그레이션은 순수 SQL(`db/migration/V{번호}__{설명}.sql`)로 쓴다.
- Flyway는 JDBC 드라이버로 **앱이 시작될 때 한 번** 실행된다. 요청은 계속 R2DBC와 Exposed로만 처리한다.
- **스키마의 주인은 Flyway다.** Exposed `SchemaUtils.create`로 테이블을 만들지 않는다.
- Exposed `Table` 정의가 Flyway 스키마와 어긋나지 않는지는 `exposed-migration-r2dbc`의 `MigrationUtils`로 통합 테스트에서 검사한다.
- Flyway는 `spring-boot-flyway` 모듈만 쓴다. 스타터(`spring-boot-starter-flyway`)는 `spring-boot-starter-jdbc`와 HikariCP를 가져와 쓰지 않는 JDBC 연결 풀이 남는다. `DataSource` 빈이 없는지 테스트로 확인한다.

## 검토한 대안

- **Liquibase**: 롤백을 지원하고 DB에 중립적이지만 변경 목록이 무겁다. PostgreSQL 전용 기능은 결국 SQL로 써야 하고, DB를 바꿀 계획도 없다.
- **R2DBC 전용 커뮤니티 마이그레이션 라이브러리**: JDBC 드라이버 하나를 뺄 수 있을 뿐이고, 사용자가 적어 유지보수 위험이 크다.
- **Exposed 마이그레이션 모듈만 사용**: Table 정의와의 차이로 SQL을 만들어 주기만 하고, 버전 관리와 적용은 하지 않는다. 공식 문서도 Flyway나 Liquibase와 함께 쓰라고 안내한다.
- **`schema.sql` 초기화**: 버전이 없어 운영 DB를 바꿀 수 없다.

## 결과

- **얻는 것**: ERD의 PostgreSQL 기능을 그대로 SQL로 쓸 수 있다. 검증된 도구가 적용을 맡고, Exposed와 스키마의 어긋남은 테스트가 잡는다.
- **감수하는 것**
  - 되돌리기(undo)는 유료 기능이라, 되돌릴 때는 새 마이그레이션을 추가한다.
  - JDBC 드라이버가 클래스패스에 있으므로 요청 경로에서 JDBC를 쓰지 않도록 주의해야 한다.
  - 무거운 마이그레이션은 시작 시간과 잠금에 영향을 준다(작성 규칙은 [영속성 규칙](../architecture/persistence.md)).
- **후속 작업**: 배포 환경이 정해지면, 마이그레이션을 앱 시작 시 실행할지 배포 단계에서 따로 실행할지 다시 검토한다.
