# Catalog 실행 이미지. jar는 CI(또는 로컬)의 ./gradlew build가 만든 build/libs의 실행 jar를 받는다.
# dozy-auth 라이브러리를 받는 GitHub 인증을 이미지 빌드 안으로 넘기지 않으려고 이미지 안에서 Gradle을 돌리지 않는다.
# 빌드: ./gradlew build && docker build -t dozy-catalog-api .

# 1단계: 실행 jar를 레이어별로 푼다. 의존성 레이어는 코드가 바뀌어도 그대로라 이미지를 다시 올릴 때 받지 않는다.
FROM eclipse-temurin:21-jre AS extractor
WORKDIR /extract
COPY build/libs/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# 2단계: 실행 이미지. 잘 바뀌지 않는 레이어부터 쌓는다.
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 1001 catalog \
    && useradd --system --uid 1001 --gid catalog --no-create-home catalog
WORKDIR /app
COPY --from=extractor /extract/extracted/dependencies/ ./
COPY --from=extractor /extract/extracted/spring-boot-loader/ ./
COPY --from=extractor /extract/extracted/snapshot-dependencies/ ./
COPY --from=extractor /extract/extracted/application/ ./
USER catalog
EXPOSE 8080
# 힙은 컨테이너 메모리 한도의 75%. 시간대는 코드가 시스템 기본값에 기대지 않지만(docs/adr/0010) 서버 기준대로 UTC로 둔다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC" \
    SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "application.jar"]
