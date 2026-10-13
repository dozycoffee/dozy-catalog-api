#!/usr/bin/env bash
# Catalog 이미지를 만든다(현재 체크아웃 기준). jar는 Gradle이 만들고, 이미지는 저장소의 Dockerfile이 그 jar를 담는다.
# 커밋 SHA가 이미지에 들어가 dozy_catalog_build_info의 revision으로 나온다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

(cd "$REPO_DIR" && ./gradlew bootJar -x test)
docker build -t dozy-catalog-api:loadtest --build-arg GIT_REVISION="$(git -C "$REPO_DIR" rev-parse HEAD)" "$REPO_DIR"
