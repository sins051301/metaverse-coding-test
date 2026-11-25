#!/usr/bin/env bash
# 서버의 authorized_keys 에 등록한 배포 키가 이 스크립트만 실행하게 묶여 있다 (docs/deployment.md 3-3).
# git pull 은 authorized_keys 의 command 에서 먼저 하므로, 여기서는 이미 새 코드로 받아진 상태다.
set -euo pipefail

cd "$(dirname "$0")/.."

docker build -q -f docker/judge/java.Dockerfile -t coding-test/judge-java docker/judge
docker build -q -f docker/judge/javascript.Dockerfile -t coding-test/judge-javascript docker/judge
docker compose -f compose.prod.yaml --env-file .env up -d --build
docker image prune -f
