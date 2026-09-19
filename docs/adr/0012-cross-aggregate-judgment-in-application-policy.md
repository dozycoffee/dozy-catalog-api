# ADR-0012: 여러 애그리거트를 보는 판단은 application 정책에 두고, domain은 다른 애그리거트를 ID로만 참조한다

- **상태**: 채택
- **날짜**: 2026-09-19
- **관련**: 이슈 #52, ADR-0001(포트 배치 부분 대체), ADR-0008(대체), [도메인 모델](../domain-model.md), [패키지 구조](../architecture/package-structure.md)

## 맥락

ADR-0008은 여러 애그리거트를 함께 보는 판단을 도메인 서비스에 두었다. 그 결과 domain 패키지끼리 ID가 아니라 모델을 직접 참조하게 됐다.
- `product`의 도메인 서비스와 `Product`의 예외 지정 메서드가 `OptionGroup`, `Option`을 받았다.
- `Product.changeCategory`가 `ChildCategory`를 받았다.
- `storedisplay`의 도메인 서비스가 `Product`, `StoreProductAvailability`를 받았다.
- `TagRegistrar`는 도메인 서비스이면서 Repository를 호출했다.

Repository 구현을 앞두고, `CategoryRepository.hasProducts`처럼 Repository가 다른 애그리거트의 데이터를 조회하는 메서드도 생기려 했다. 이러면 애그리거트 사이의 의존이 domain과 Repository 곳곳으로 퍼진다. 어떤 코드가 여러 애그리거트를 알아도 되는지를 한 줄로 말할 수 있는 규칙이 필요했다.

## 결정

### 의존 규칙
- domain의 각 애그리거트 패키지는 **자기 자신, `domain.shared`, 다른 애그리거트의 ID**(`*Id` 타입과 논리 식별자 `OptionKey`)만 참조한다.
- 애그리거트 혼자 지킬 수 있는 불변식은 애그리거트 메서드에 둔다. 다른 애그리거트의 정보가 필요하면 application이 조회해 ID나 값(예: 옵션 그룹의 옵션 키 목록)으로 넘긴다.
- 여러 애그리거트를 함께 보는 판단은 **application의 정책 클래스**(`application/<module>/policy`)에 둔다. 정책은 I/O 없이 넘겨받은 값만으로 판단하는 순수 클래스이며, 애그리거트를 바꾸지 않고 결과만 돌려준다. ApplicationService 안에 녹이지 않는다.
- ADR-0008의 나머지 결정은 그대로 이어받는다. 애그리거트는 자기 상태를 스스로 바꾸고, application 서비스가 조회·잠금·정책 호출·변경 요청·저장·이벤트 발행과 트랜잭션 경계를 맡는다. 옵션 목록 교체는 OptionGroup과 연결 Product들을 한 트랜잭션에서 바꾼다.

### 인터페이스와 구현 배치

| 종류 | 인터페이스 위치 | 구현 (infrastructure) | 구현이 쓰는 것 |
|---|---|---|---|
| Repository | `domain/<module>` | `persistence/<module>/Exposed<Module>Repository` | 자기 애그리거트 테이블만 |
| 외부 시스템 포트 | `application/<module>/port` | `acl/…Adapter` | Store BC 등의 Client(번역 포함) |
| 조회 포트(여러 애그리거트에 걸친 읽기) | `application/<module>/port` | `persistence/query/…` | Exposed로 테이블 직접 조회 |

- Repository는 자기 애그리거트만 다룬다. 자기 테이블에 대한 단순 존재 조회(예: `ProductRepository.existsByCategory`)는 그 애그리거트의 Repository에 둔다.
- 포트 구현체는 domain Repository를 호출해 조합하지 않는다. 무엇을 불러와 어떻게 합칠지는 오케스트레이션이므로 application에 둔다.
- ADR-0001의 "포트는 외부 시스템 연동에만"을 이 표로 대체한다. 레이어 우선 패키지 구조와 의존 방향은 그대로다.

## 검토한 대안

- **지금 구조 유지(ADR-0008, 판단은 도메인 서비스)**: 판단은 테스트하기 쉽지만, 도메인 서비스와 애그리거트 메서드가 다른 애그리거트의 모델을 받으면서 domain 안의 의존 경계가 흐려진다. 어디까지 참조해도 되는지 기준이 없다.
- **판단을 ApplicationService 안에 직접 작성**: ADR-0008이 거부한 방식이다. 규칙이 유스케이스마다 흩어지고, 즉시 변경과 예약 적용에서 같은 규칙이 중복되며, 저장소 없이 테스트하기 어렵다. 판단을 별도 순수 정책 클래스로 두어 이 문제를 피한다.
- **모든 outbound 의존(Repository, 외부 시스템)을 domain의 포트로 정의**: 계약을 도메인 언어로 정의할 수 있지만, 판단이 도메인 서비스에 남아 있는 한 애그리거트 사이의 모델 참조 문제는 그대로다.
- **포트 구현체가 Repository와 Client를 조합**: application이 Repository를 직접 부르면 되는 경우 포트가 단순 전달층이 되고, 흐름이 application과 infrastructure로 나뉘며 잠금과 트랜잭션 경계가 infrastructure 안에 숨는다.

## 결과

- **얻는 것**
  - "여러 애그리거트를 아는 코드는 application뿐"이라는 한 줄 규칙이 생긴다. domain 애그리거트 패키지는 서로 ID로만 연결된다.
  - 판단은 여전히 순수 클래스 단위로 테스트된다.
  - Repository와 포트의 역할이 나뉜다.
- **감수하는 것**
  - `Product.changeCategory(ChildCategory)`가 주던 "소분류만" 타입 보장이 사라진다. application이 `Category.requireChild()`로 확인한 뒤 `CategoryId`를 넘긴다.
  - application이 두꺼워진다. 애그리거트 내부 불변식은 애그리거트에 남으므로 빈약한 도메인 모델이 되지는 않는다.
  - 의존 규칙은 지금 코드 리뷰로만 지킨다.
- **후속 작업**
  - 의존 규칙을 테스트(Konsist 등)로 강제할지는 [미정 사항](../architecture/README.md#미정-사항)에서 논의한다.
  - Repository 구현(#47, #48, #51)은 이 배치를 따른다.
