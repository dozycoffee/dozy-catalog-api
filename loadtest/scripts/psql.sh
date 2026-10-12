#!/usr/bin/env bash
# DB에 psql로 붙는다. 인자는 psql에 그대로 넘긴다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
"${COMPOSE[@]}" exec postgres psql -U catalog -d catalog "$@"
