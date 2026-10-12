#!/usr/bin/env bash
# 인프라를 내린다. 데이터까지 지우려면 --volumes.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
"${COMPOSE[@]}" down "$@"
