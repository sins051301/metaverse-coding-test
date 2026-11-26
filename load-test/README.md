# 부하 테스트 (k6)

`docs/tuning.md`의 임시값(`judge.concurrency`, `judge.queue-capacity`, `judge.per-user-unfinished-limit`)을 실제 측정으로 다시 정하기 위한 도구다. CI에는 넣지 않고 직접 돌린다.

## 무엇을 재는가

가상 유저(VU) 한 명 = 테스트 유저 한 명이다. VU는 다음을 반복한다.

1. 문제 1(짝수 개수 세기) 정답을 제출한다 → `202`
2. 1초마다 `GET /api/submissions/{id}`로 폴링한다 (프론트와 같은 간격)
3. `DONE`/`FAILED`가 되면 2초 쉬고 다시 제출한다

VU 수는 `RAMP` 동안 0에서 `PEAK_VUS`까지 늘고, `HOLD` 동안 유지되다가 30초에 걸쳐 0으로 줄어든다.

| 지표 | 뜻 | 기준 |
|---|---|---|
| `judge_turnaround` | 접수(`202`)부터 채점 결과가 나올 때까지 걸린 시간. 대기 + 채점 + 폴링 간격(최대 1초) | p95 < 60초 (`queue-capacity`를 정할 때 쓴 허용 대기) |
| `http_req_duration{name:POST ...}` | 제출 접수 응답 시간. 채점은 비동기라 여기엔 들어가지 않는다 | p95 < 1초 |
| `submission_rejected` | 대기열 상한(끝나지 않은 제출+실행 50건)을 넘어 `429`로 거절된 비율 | 기준 없음. 상한이 동작하는지 보는 값 |
| `http_req_failed` | 5xx·연결 실패 비율. `429`는 정상 동작이라 실패로 세지 않는다 | < 1% |
| `judge_not_accepted`, `judge_timed_out` | AC가 아닌 결과, 180초 안에 끝나지 않은 건수 | 0 |

같은 시간대의 서버 쪽 모습은 Grafana "코딩 테스트 채점" 대시보드에서 본다. `judge_slots_waiting`(대기 건수)과 `judge_slot_wait_seconds`(자리를 기다린 시간)를 보면, 늦어진 원인이 자리 부족인지 채점 자체가 느려진 것인지 나뉜다.

## 실행

k6 설치: `winget install GrafanaLabs.k6`

### 1. 테스트 유저 만들기

유저별 진행 중 개수 제한(2건) 때문에 VU마다 다른 유저가 필요하다. `load-test-1`부터 `load-test-1000`까지 만들고 id 목록을 출력한다. 다시 실행해도 중복으로 만들지 않는다.

```bash
# 로컬
docker exec -i coding-test-mysql-1 mysql -N -uroot -proot coding_test < load-test/users.sql
# 운영 (서버에서)
docker compose -f compose.prod.yaml --env-file .env exec -T mysql sh -c 'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" coding_test' < load-test/users.sql
```

### 2. 부하 걸기

Google 로그인은 거칠 수 없으므로 서버와 같은 `JWT_SECRET`으로 유저별 토큰을 직접 서명한다 (`deploy/smoke-test.mjs`와 같은 방식).

```bash
k6 run -e BASE_URL=http://localhost:8080 -e JWT_SECRET=... -e USER_IDS=3,4,5,... \
  -e PEAK_VUS=60 -e RAMP=2m -e HOLD=3m load-test/submission.js
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | (필수) | 로컬 `http://localhost:8080`, 운영 `https://pixelmogako.store` |
| `JWT_SECRET` | (필수) | 서버의 `JWT_SECRET` |
| `USER_IDS` | (필수) | 1단계에서 출력된 id 목록 |
| `PEAK_VUS` | 60 | 최대 VU 수. 테스트 유저 수를 넘을 수 없다 |
| `RAMP`, `HOLD` | `2m`, `3m` | 늘리는 시간, 유지하는 시간 |
| `LANGUAGE` | `JAVA` | `JAVA` 또는 `JAVASCRIPT` |
| `REJECTED_WAIT_SECONDS` | 2 | `429`로 거절된 뒤 다시 제출하기까지 쉬는 시간의 평균(0.5~1.5배 사이 무작위). 실제 사용자처럼 보려면 20 정도 |

k6는 채점 서버 밖에서 돌린다. 서버 안에서 돌리면 k6가 `javac`와 CPU를 나눠 써서 측정이 틀어진다.

### 3. 정리

```bash
docker exec -i coding-test-mysql-1 mysql -uroot -proot coding_test < load-test/cleanup.sql
```

테스트 유저와 그 제출·푼 문제 기록을 지운다.

## 채점 1건 측정 (`judge-bench.sh`)

k6는 서버 전체(대기열·DB·폴링 포함)를 재고, 이 스크립트는 백엔드 없이 **채점 컨테이너 하나**만 잰다. 채점 이미지를 바꿨을 때 어디가 얼마나 줄었는지 보는 용도다.

```bash
bash load-test/judge-bench.sh coding-test/judge-java 20 1   # 이미지, 실행기마다 반복 수, 동시 실행 수
bash load-test/judge-bench.sh coding-test/judge-java 5 4    # 4건 동시 (운영의 judge.concurrency=4 와 같은 경쟁)
```

- 컨테이너 옵션은 `SandboxRunner`와 같다 (메모리 265MB, CPU 1, 네트워크 차단 등). 문제 1 정답 코드와 테스트 10개를 넣는다.
- 반복마다 컨테이너를 두 번 띄운다: 한 번은 실제 채점(`runner.sh`) 전체, 한 번은 같은 조건에서 `javac`만.

| 출력 | 뜻 |
|---|---|
| 컨테이너 전체 | `docker run` 시작부터 결과 tar를 받을 때까지. 서버의 `judge.slot.run`에 해당 |
| `javac` | 컴파일 1번 |
| 테스트 1개 `java` | 테스트 10개 실행 시간 합 ÷ 10 (`.meta`의 `time_seconds`) |
| 테스트 최대 메모리 | 테스트 중 최대 RSS. 이미지를 바꾼 뒤 늘면 MLE 판정이 달라질 수 있다 |
| 정답 아닌 실행 | 반환값이 정답과 다른 횟수. 0이어야 한다 |

비교할 이미지는 태그를 나눠 빌드한다 (예: `docker build -f java.Dockerfile -t coding-test/judge-java:before .`).

### 측정 기록

| 날짜 | 환경 | 이미지 | 조건 | 컨테이너 전체 | `javac` | 테스트 1개 `java` | 최대 메모리 |
|---|---|---|---|---|---|---|---|
| 2026-11-25 | 노트북 (16코어, Docker 28.2) | 이전 | 동시 1건 × 20 | 4,873ms (p95 5,928) | 2,622ms | 84ms | 57.8MB |
| 2026-11-25 | 노트북 | 래퍼 미리 컴파일 | 동시 1건 × 20 | 3,916ms (p95 5,562) | 1,416ms | 102ms | 58.3MB |
| 2026-11-25 | 노트북 | 래퍼 + AOT 캐시 | 동시 1건 × 20 | 2,372ms (p95 3,784) | 568ms | 69ms | 56.5MB |
| 2026-11-25 | 노트북 | 이전 | 동시 4건 × 5 | 6,626ms (p95 8,497) | 3,172ms | 130ms | 57.6MB |
| 2026-11-25 | 노트북 | 래퍼 미리 컴파일 | 동시 4건 × 5 | 5,468ms (p95 6,771) | 2,534ms | 126ms | 58.3MB |
| 2026-11-25 | 노트북 | 래퍼 + AOT 캐시 | 동시 4건 × 5 | 4,248ms (p95 5,366) | 1,021ms | 96ms | 57.4MB |
| 2026-11-25 | 노트북 (다른 시간대) | 래퍼 + AOT 캐시 (C1 비교 기준) | 동시 1건 × 20 | 2,706ms (p95 3,026) | 720ms | 78ms | 56.1MB |
| 2026-11-25 | 노트북 | + `javac` C1만 | 동시 1건 × 20 | 2,292ms (p95 2,485) | 364ms | 78ms | 56.4MB |
| 2026-11-25 | 노트북 | + `javac`·`java` 모두 C1만 (채택 안 함) | 동시 1건 × 20 | 2,166ms (p95 2,388) | 374ms | 62ms | 51.7MB |
| 2026-11-25 | 노트북 (다른 시간대) | 래퍼 + AOT 캐시 (C1 비교 기준) | 동시 4건 × 5 | 2,983ms (p95 3,450) | 900ms | 81ms | 56.8MB |
| 2026-11-25 | 노트북 | + `javac` C1만 | 동시 4건 × 5 | 2,691ms (p95 3,038) | 522ms | 77ms | 56.2MB |
| 2026-11-25 | 노트북 | + `javac`·`java` 모두 C1만 (채택 안 함) | 동시 4건 × 5 | 3,146ms (p95 3,986) | 521ms | 90ms | 50.9MB |

모두 중간값, 정답 아닌 실행 0건. 해석은 README 7장 "Java 시작 비용 줄이기".

## 값을 읽는 법

- VU 50명 이하에서는 `submission_rejected`가 0이어야 한다. 끝나지 않은 건이 VU 수를 넘지 않기 때문이다.
- VU가 50명을 넘으면 `429`가 나기 시작한다. 이때 `judge_turnaround` p95가 60초 안이면 상한이 맨 뒤 사람의 대기를 잘 막고 있다는 뜻이다.
- 처리량(초당 끝나는 채점 수)은 `judge.concurrency ÷ 1건 채점 시간`에서 더 늘지 않는다. `concurrency`를 바꿔 가며 같은 조건으로 돌려 처리량이 더 이상 늘지 않는 값을 찾는다.

## 측정 기록

| 날짜 | 환경 | 조건 | `judge_turnaround` p95 | 접수 p95 | `429` 비율 | 비고 |
|---|---|---|---|---|---|---|
| 2026-11-24 | 로컬 노트북 (bootRun, concurrency 4) | VU 3, 70초, Java | 7초 | 90ms | 0% | 스크립트 검증용. 모두 AC |
| 2026-11-24 | 운영 (Oracle A1 4 OCPU/24GB, concurrency 4, queue 50) | VU 30, 1분 증가 + 3분 유지, Java | 30.8초 (p50 29.5초) | 114ms | 0% | 대기 최대 26, 채점 1건 평균 4.25초·p95 5.5초, 처리량 0.94건/초, CPU 94% (`system_cpu_usage`) |
| 2026-11-24 | 운영 (같음) | VU 60, 1분 증가 + 3분 유지, Java | 54.8초 (p50 52.7초) | 132ms | 71% (거절된 VU가 2초마다 다시 시도해서 부풀려진 값) | 대기 최대 48 + 채점 중 4 = 52 (check-then-act 로 상한 50을 2건 넘음), 처리량 0.94건/초로 VU 30과 같음, 호스트 `vmstat` 유지 구간 idle 5%·실행 대기 6~11, 메모리 여유 21GB |
| 2026-11-24 | 운영 (같음, Grafana Cloud 전송 중) | VU 1000, 2분 증가 + 3분 유지, Java, `REJECTED_WAIT_SECONDS=20` | 59.2초 (p50 55.9초, max 62초) | 85ms | 97% (시도 12,733번 중 12,388번) | 대기 최대 50 + 채점 중 4 = 54, 처리량 0.89건/초, 채점 1건 평균 4.49초, CPU 95%(백엔드 JVM 7.5%), 초당 요청 약 96건(거절 POST 47건)에도 DB 커넥션 사용 최대 4/10·대기 0, 힙 최대 163MB |
| 2026-11-25 | 운영 (같음, **래퍼 미리 컴파일 + AOT 캐시 적용**, PR #6) | VU 60, 1분 증가 + 3분 유지, Java | 31.9초 (p50 29.7초, max 33.0초) | 111ms | 55% (시도 1,024번 중 559번, 2초 재시도로 부풀려진 값) | **처리량 1.65건/초 (어제 0.94 → +75%)**, 채점 1건 평균 2.43초·p95 2.78초 (어제 VU 30 기준 4.25초·5.5초), 대기 최대 47 + 채점 중 4, 자리 대기 평균 27.5초, CPU 91%(백엔드 JVM 7.9%), 465건 모두 AC |
| 2026-11-25 | 운영 (같음, + `javac` C1만, PR #12 · 측정 후 되돌림) | VU 60, 1분 증가 + 3분 유지, Java | 32.5초 (p50 28.5초, max 33.7초) | 109ms | 53% (시도 1,028번 중 549번) | 처리량 1.69건/초 (+2.5%), 채점 1건 평균 2.37초·p95 3.07초, 대기 최대 47 + 채점 중 4, 자리 대기 평균 26.8초, CPU 87%(백엔드 JVM 8.5%), 479건 모두 AC. 측정 1분 전 백엔드 재시작(PR #8 함께 배포) |
