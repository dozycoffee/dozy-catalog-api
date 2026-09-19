# ADR (Architecture Decision Record)

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

되돌리기 비싼 설계 결정을 **왜** 그렇게 정했는지 남긴다. 도메인 모델과 아키텍처 문서는 현재 규칙을 설명하고, 그 근거는 여기 ADR로 링크한다.

## 작성 기준

다음 중 하나에 해당하면 ADR을 쓴다. 사소한 선택은 쓰지 않는다.
- 되돌리기 비싼 결정(데이터 구조, 모듈 경계, 외부 계약 등)
- 여러 문서나 모듈에 걸친 결정
- 대안을 실제로 검토하고 하나를 고른 결정

## 운영 규칙

- **번호는 작성 순서대로 붙이고 바꾸지 않는다.** 번호가 식별자라서 다른 문서와 코드에서 `ADR-0005`처럼 참조한다. 읽는 순서는 아래 주제별 목록으로 잡는다.
- **채택된 ADR은 고치지 않는다.** 결정이 바뀌면 새 ADR을 쓰고, 기존 ADR은 상태만 `대체됨 (→ ADR-NNNN)`으로 바꾼다. 결정의 일부만 바뀌면 `일부 대체됨 (바뀐 부분 → ADR-NNNN)`으로 쓴다. 오타·링크 수정은 예외.
- **새 결정은 그 결정을 반영하는 코드·문서 PR에서 ADR을 함께 추가한다.**
- 파일명은 `NNNN-english-kebab-case.md`, 제목과 본문은 한국어. 새 ADR은 [템플릿](0000-template.md)을 복사해 쓴다.
- 0001~0008은 이미 내린 결정을 소급 작성했다. 날짜는 실제 결정(해당 PR 머지) 날짜다.

## 목록 (주제별)

### 문서와 개발 방식
| ADR | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [0004](0004-docs-in-repo-as-source-of-truth.md) | 명세 원본을 저장소 `docs/`로 옮기고 Notion은 참고용으로 둔다 | 채택 | 2026-09-18 |

### 아키텍처
| ADR | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [0001](0001-layered-packages-ports-for-external-only.md) | 레이어 우선 패키지 구조를 쓰고, 포트는 외부 시스템 연동에만 둔다 | 일부 대체됨 (포트 배치 → 0012) | 2026-08-17 |
| [0002](0002-error-code-and-error-type.md) | 도메인 예외는 `ErrorCode`/`ErrorType`으로 분류하고 domain은 HTTP를 모른다 | 채택 | 2026-09-18 |
| [0007](0007-domain-exception-vs-require-check.md) | 사용자가 일으킬 수 있는 규칙 위반은 `DomainException`, 호출 코드 오류는 `require`/`check` | 채택 | 2026-09-19 |
| [0008](0008-judgment-in-domain-service-orchestration-in-application.md) | 판단은 도메인 서비스, 오케스트레이션과 트랜잭션 경계는 application | 대체됨 (→ 0012) | 2026-09-19 |
| [0011](0011-transaction-boundary-with-transaction-runner.md) | 트랜잭션 경계는 application의 `TransactionRunner`로 연다 | 채택 | 2026-09-19 |
| [0012](0012-cross-aggregate-judgment-in-application-policy.md) | 여러 애그리거트를 보는 판단은 application 정책에 두고, domain은 다른 애그리거트를 ID로만 참조한다 | 채택 | 2026-09-19 |

### 영속성
| ADR | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [0009](0009-flyway-with-exposed-schema-check.md) | 스키마 마이그레이션은 Flyway(JDBC)로 하고, Exposed로 스키마 불일치를 검사한다 | 채택 | 2026-09-19 |
| [0010](0010-schema-conventions-and-time.md) | 스키마 기본 규칙과 시간 처리 | 일부 대체됨 (동시 수정 → 0013) | 2026-09-19 |
| [0013](0013-optimistic-locking-for-product-and-option-group.md) | 낙관적 잠금은 Product와 OptionGroup에만 쓰고, 충돌은 사람이 다시 판단한다 | 채택 | 2026-09-19 |

### 도메인 모델
| ADR | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [0003](0003-keep-store-settings-on-discontinue.md) | 단종·재활성화 때 매장 설정을 바꾸지 않는다 | 채택 | 2026-09-18 |
| [0005](0005-split-display-setting-and-availability.md) | 진열 설정과 판매 가능 여부를 분리하고, 재고는 이벤트 투영으로 둔다 | 채택 | 2026-09-18 |
| [0006](0006-catalog-pricing-boundary.md) | Catalog는 가격 데이터·유효 옵션 구성·표시용 시작가까지만 제공한다 | 채택 | 2026-09-19 |
