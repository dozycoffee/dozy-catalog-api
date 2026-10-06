plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ktlint)
}

group = "com.dozycoffee"
// 배포 버전은 태그에서 받는다(-PappVersion=0.1.0, .github/workflows/release.yml). 로컬 빌드는 SNAPSHOT이다.
version = providers.gradleProperty("appVersion").getOrElse("0.1.0-SNAPSHOT")
description = "dozy-catalog-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    // dozy-auth 라이브러리. GitHub Packages는 공개 패키지도 인증이 필요하다(read:packages 권한의 토큰).
    // 로컬은 ~/.gradle/gradle.properties의 gpr.user·gpr.key 또는 환경 변수 GITHUB_ACTOR·GITHUB_TOKEN, CI는 GITHUB_TOKEN을 쓴다.
    maven {
        url = uri("https://maven.pkg.github.com/dozycoffee/dozy-auth")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
            password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
        }
        content { includeGroup("com.dozycoffee.auth") }
    }
}

// Spring Boot BOM이 관리하는 kotlinx-coroutines 버전을 Exposed가 요구하는 버전으로 올린다(libs.versions.toml 참고).
extra["kotlin-coroutines.version"] =
    libs.versions.kotlinx.coroutines
        .get()

// Spring Boot BOM이 관리하는 r2dbc-postgresql 버전을 행 누락 버그가 고쳐진 버전으로 올린다(libs.versions.toml 참고).
extra["r2dbc-postgresql.version"] =
    libs.versions.r2dbc.postgresql
        .get()

configurations {
    // Spring Boot Gradle 플러그인 기본값은 developmentOnly를 testRuntimeClasspath까지 전파한다.
    // 테스트는 spring-boot-docker-compose 대신 Testcontainers(@ServiceConnection)로 DB를 붙이므로 제외.
    testRuntimeOnly {
        exclude(group = "org.springframework.boot", module = "spring-boot-docker-compose")
    }
}

dependencies {
    // presentation / application — 리액티브 웹, 보안
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.dozy.auth.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.reactor.kotlin.extensions)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.logging.jvm)

    // infrastructure/persistence — R2DBC ConnectionFactory(Spring Boot 자동구성) + Exposed DSL
    implementation(libs.spring.boot.starter.r2dbc)
    implementation(libs.r2dbc.pool)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.java.time)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.r2dbc.postgresql)

    // infrastructure/persistence — 스키마 마이그레이션(Flyway, 앱 시작 시 JDBC로 한 번 실행)
    implementation(libs.spring.boot.flyway)
    implementation(libs.flyway.database.postgresql)

    // local dev
    developmentOnly(libs.spring.boot.devtools)
    developmentOnly(libs.spring.boot.docker.compose)

    // infrastructure/config — @ConfigurationProperties 메타데이터 생성
    annotationProcessor(libs.spring.boot.configuration.processor)
    kapt(libs.spring.boot.configuration.processor)

    // test
    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.dozy.auth.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test"))

    // test — Exposed 영속성 코드 대상 통합 테스트
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.r2dbc)
    testImplementation(libs.exposed.migration.core)
    testImplementation(libs.exposed.migration.r2dbc)
}

// 이미지에는 실행 jar 하나만 넣는다. plain jar가 함께 생기면 Dockerfile이 어느 jar를 쓸지 모호해진다.
tasks.jar {
    enabled = false
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 코드는 시스템 기본 시간대에 의존하지 않는다(docs/adr/0010). 개발 PC(KST)와 CI(UTC)에서 결과가 같도록 고정한다.
    systemProperty("user.timezone", "UTC")
}

// Docker(Testcontainers) 없이 빠르게 돌리는 단위 테스트. IntegrationTest를 상속한 테스트는 integration 태그가 붙는다.
// 전체는 ./gradlew test로 돌린다(CI도 build로 전체를 돌린다).
tasks.register<Test>("unitTest") {
    description = "통합 태그가 없는 테스트만 실행한다 (Docker 불필요)"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { excludeTags("integration") }
}
