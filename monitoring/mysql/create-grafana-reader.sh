#!/bin/sh
# Grafana 가 DB 패널을 그릴 때 쓰는 읽기 전용 계정을 만든다. 이미 있으면 비밀번호만 다시 맞춘다.
# 새 MySQL 볼륨: /docker-entrypoint-initdb.d 에 연결돼 처음 시작할 때 자동 실행된다.
# 이미 데이터가 있는 MySQL(운영 서버 등): 직접 한 번 실행한다 (docs/deployment.md "모니터링")
#   docker compose -f compose.prod.yaml --env-file .env exec mysql sh /docker-entrypoint-initdb.d/create-grafana-reader.sh
set -eu

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'grafana_reader'@'%' IDENTIFIED BY '${GRAFANA_MYSQL_PASSWORD}';
ALTER USER 'grafana_reader'@'%' IDENTIFIED BY '${GRAFANA_MYSQL_PASSWORD}';
GRANT SELECT ON coding_test.* TO 'grafana_reader'@'%';
SQL
