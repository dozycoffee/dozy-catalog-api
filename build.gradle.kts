plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ktlint)
}

group = "com.dozycoffee"
version = "0.0.1-SNAPSHOT"
description = "dozy-catalog-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

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
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.reactor.kotlin.extensions)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.jackson.module.kotlin)

    // infrastructure/persistence — R2DBC ConnectionFactory(Spring Boot 자동구성) + Exposed DSL
    implementation(libs.spring.boot.starter.r2dbc)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.r2dbc.postgresql)

    // local dev
    developmentOnly(libs.spring.boot.devtools)
    developmentOnly(libs.spring.boot.docker.compose)

    // infrastructure/config — @ConfigurationProperties 메타데이터 생성
    annotationProcessor(libs.spring.boot.configuration.processor)
    kapt(libs.spring.boot.configuration.processor)

    // test
    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test"))

    // test — Exposed 영속성 코드 대상 통합 테스트
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.r2dbc)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
