# ADR-0019: 서버 이미지는 Dockerfile로 만들어 main 병합마다 올리고, 버전 태그는 그 이미지에 덧붙인다

- **상태**: 채택
- **날짜**: 2026-10-06
- **관련**: #116, [README 배포](../../README.md#배포), dozy-auth의 서버 이미지 규칙([dozy-auth#117](https://github.com/dozycoffee/dozy-auth/issues/117), [dozy-auth#119](https://github.com/dozycoffee/dozy-auth/issues/119))

## 맥락

Catalog를 컨테이너 이미지로 배포해야 한다. dozy-platform이 여러 서비스를 compose로 함께 띄우는 개발 환경을 구성하고 있어서, 서비스마다 이미지 이름·태그·버전 붙이는 방식이 같아야 버전 조합을 관리하기 쉽다.

dozy-auth는 서버 이미지 규칙을 먼저 정했다.
- `main` 병합마다 `sha-{7자리}`·`main` 태그로 올린다(#117).
- 사람이 읽는 시맨틱 버전은 그 이미지에 태그로 덧붙인다(#119).

Catalog는 이 규칙을 따른다. 빌드에는 GitHub Packages 인증이 필요하다(dozy-auth 라이브러리).

## 결정

- **이미지는 Dockerfile로 만든다.** dozy-auth 서버 이미지와 같은 틀을 쓴다.
  - 베이스는 `eclipse-temurin:21-jre-noble`이다. JDK major와 OS를 고정하고, 21의 patch는 빌드할 때마다 받는다.
  - 실행 jar를 `jarmode=tools extract --layers --launcher`로 풀어 계층을 나눈다.
  - root가 아닌 UID·GID 10001로 실행한다.
  - 힙은 컨테이너 메모리 한도의 75%(`MaxRAMPercentage`)로 둔다.
  - 프로필과 비밀값은 실행할 때 환경 변수로 준다.
- **jar는 이미지 밖(CI의 Gradle)에서 테스트를 통과한 뒤 만든 것을 담는다.** GitHub Packages 인증을 이미지 빌드 안으로 넘기지 않는다.
- **`linux/amd64`와 `linux/arm64`를 한 태그로 함께 만든다.**
  - Apple Silicon PC에서 dozy-platform의 compose로 띄울 때 amd64 이미지는 에뮬레이션으로 돌아 JVM 시작이 크게 느려진다.
  - jar를 푸는 단계는 결과가 아키텍처와 무관해 빌드 머신 아키텍처로 한 번만 돌린다.
- **`main` 병합마다 CI가 `sha-{7자리}`와 `main` 태그로 이미지를 올린다.** PR에서는 빌드만 확인한다.
- **릴리스는 다시 빌드하지 않는다.** main 커밋에 `v{major}.{minor}.{patch}` 태그를 푸시하면 워크플로가 다음을 차례로 한다.
  1. 태그 커밋이 main에 있는지 확인한다.
  2. 그 커밋의 `sha-` 이미지에 `X.Y.Z` 태그를 덧붙인다.
  3. GitHub Release를 만든다.
  - 이미 있는 버전 태그는 덮어쓰지 않는다.
  - Catalog 저장소는 결과물이 서버 하나라 git 태그에 `server-` 접두어를 붙이지 않는다. dozy-auth는 라이브러리와 서버가 한 저장소에 있어 `server-v*`를 쓴다.
- **이동 태그(`latest`, `0.1`)는 두지 않는다.** 어떤 버전이 떠 있는지 모호해진다.
- **버전은 SemVer를 따르고 0.x로 시작한다(`v0.1.0`).**
  - 0.x 동안 API 호환이 깨지는 변경과 기능 추가는 minor, 버그 수정·내부 개선은 patch를 올린다. 1.0.0은 운영 배포 시점에 정한다.
  - API 경로의 `/api/v1`은 API 계약의 버전이라 이미지 버전과 따로 간다.
  - jar에는 버전을 넣지 않는다. 실행 중인 버전은 이미지의 `revision`(커밋)과 GitHub Release로 확인한다.

## 검토한 대안

- **Spring Boot `bootBuildImage`(Cloud Native Buildpacks)**
  - 좋은 점: 레이어 분리, JVM 메모리 계산, root 아닌 사용자, 베이스 갱신을 알아서 해 준다.
  - 택하지 않은 이유: 무엇이 들어가는지 잘 보이지 않고, 바꾸려면 Buildpacks 설정(`BP_*`)을 알아야 하며, dozy-auth와 틀이 달라진다.
- **릴리스 때 다시 빌드**: 태그를 푸시하면 빌드·테스트를 다시 돌려 버전 이미지를 새로 만든다. 처음 초안이 이 방식이었다.
  - 좋은 점: jar에 버전을 넣을 수 있다.
  - 택하지 않은 이유: 운영에 올라가는 이미지가 main에서 테스트한 이미지와 달라질 수 있다(베이스 이미지 patch, 의존성 캐시 등). dozy-auth와도 다르다.
- **이미지 안에서 Gradle로 빌드(멀티 스테이지)**: GitHub Packages 토큰을 빌드 비밀로 넘겨야 하고, Gradle 캐시를 이미지 빌드에서 따로 관리해야 한다.
- **`linux/amd64`만 만들기**: 빌드가 조금 빠르지만, Apple Silicon PC에서 이미지를 띄우는 개발 환경에서 에뮬레이션으로 돈다.

## 결과

- **얻는 것**
  - dozy-auth와 이미지 규칙이 같아, dozy-platform이 서비스 버전을 같은 방식으로 고정·갱신할 수 있다.
  - 운영에 올라가는 이미지가 main에서 테스트를 통과한 바로 그 이미지다.
  - `main` 태그로 개발 환경이 항상 최신 main을 받을 수 있고, 버전 태그로 고정된 조합을 쓸 수 있다.
  - 코드만 바뀌면 앱 계층(수 MB)만 다시 올라간다.
- **감수하는 것**
  - main 병합마다 이미지가 쌓인다. 오래된 `sha-` 이미지 정리 정책이 필요하다.
  - 베이스 이미지 보안 patch는 main에 병합이 일어나야 반영된다. 오래 병합이 없으면 다시 빌드할 방법(수동 실행 등)이 필요하다.
  - 버전 태그는 main CI가 끝난 뒤에 붙일 수 있다.
- **후속 작업**
  - 패키지 공개 범위와 배포 환경·개발 PC의 pull 권한을 정한다.
  - 실행 중인 버전을 지표(`build_info`)와 기동 로그로 확인한다(#118).
  - 오래된 `sha-` 이미지 정리 정책을 정한다.
  - 배포 대상 환경이 정해지면 마이그레이션 실행 시점을 다시 본다.
