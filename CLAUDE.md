# Dozy Catalog API

## 명세

명세 원본은 `docs/`다. 문서마다 한 가지 질문에만 답한다 (자세한 역할은 `docs/README.md`).
규칙이 바뀌면 요구사항 → 시나리오 → 도메인 모델 → ERD 순서로 고치고, 설계를 바꾸는 코드 변경에서는 관련 문서도 같은 PR에서 고친다.
되돌리기 비싼 결정은 `docs/adr/`에 ADR을 추가한다(작성 기준·운영 규칙은 `docs/adr/README.md`). 채택된 ADR은 고치지 않고 새 ADR로 대체한다.

@docs/README.md
@docs/requirements.md
@docs/scenarios.md
@docs/domain-model.md
@docs/erd.md
@docs/architecture/README.md
@docs/architecture/tech-stack.md
@docs/architecture/package-structure.md
@docs/architecture/exception.md
@docs/architecture/testing.md
@docs/adr/README.md

## 개인 설정

개인 에이전트 지침은 gitignore된 `CLAUDE.local.md`에 둔다. 로컬 전용 초안·메모는 gitignore된 `context/`에 둔다.
