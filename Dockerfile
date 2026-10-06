# Catalog 실행 이미지. 이미지 규칙은 docs/adr/0019, 실행에 필요한 설정은 README의 배포 절.
#
# jar는 Gradle이 테스트를 통과한 뒤 만든 것을 그대로 담는다. 이미지 안에서 빌드하지 않는다.
# (dozy-auth 라이브러리를 받는 GitHub 인증을 이미지 빌드 안으로 넘기지 않기 위해서다.)
#   ./gradlew bootJar
#   docker build -t dozy-catalog-api .
#
# DB 비밀번호 같은 비밀값과 프로필은 이미지에 넣지 않고 실행할 때 환경 변수로 준다.

# JDK major와 OS(Ubuntu noble)를 고정하고 21의 patch 업데이트는 빌드할 때마다 받는다
ARG JRE_IMAGE=eclipse-temurin:21-jre-noble

# 1단계: jar를 Spring Boot 계층별 폴더로 푼다. 푼 결과는 아키텍처와 무관하므로
# 빌드 머신의 아키텍처로 한 번만 돌린다(여러 아키텍처를 만들 때 에뮬레이션을 피한다).
FROM --platform=$BUILDPLATFORM ${JRE_IMAGE} AS extract
WORKDIR /build
COPY build/libs/dozy-catalog-api.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# 2단계: 실행 이미지. 대상 아키텍처(linux/amd64, linux/arm64)마다 만든다.
# 자주 바뀌지 않는 계층부터 담아 이미지 계층 캐시를 살린다
FROM ${JRE_IMAGE}

LABEL org.opencontainers.image.title="dozy-catalog-api" \
      org.opencontainers.image.description="Dozy Catalog 서버" \
      org.opencontainers.image.source="https://github.com/dozycoffee/dozy-catalog-api"

# root가 아닌 고정 UID·GID(10001)로 실행한다(dozy-auth 서버 이미지와 같음)
RUN groupadd --system --gid 10001 catalog \
    && useradd --system --uid 10001 --gid catalog --no-create-home --shell /usr/sbin/nologin catalog

WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

# 힙 최대치를 컨테이너 메모리 제한의 75%로 잡는다. JVM 기본값(25%)은 JVM만 도는 컨테이너에서 메모리를 대부분 놀린다.
# 실행할 때 JAVA_TOOL_OPTIONS를 다시 주면 덮어쓴다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
