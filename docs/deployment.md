# 배포 설계

> 작성: 2026-11-17. 배포 파일: `compose.prod.yaml`, `backend/Dockerfile`, `frontend/Dockerfile`, `deploy/Caddyfile`, `deploy/.env.example`

## 1. 한눈에 보기

```
브라우저 ── https://도메인 ──> [Caddy :443]  (오라클 Ampere A1 서버 한 대, 4 OCPU / 24GB, 오사카)
                                  │
                ┌─────────────────┼──────────────────────────┐
                │ /api/* /ws      │ 그 외 모든 경로            │
                │ /oauth2/*       │ (SPA: 없는 경로는 index.html)│
                │ /login/oauth2/* │                            │
                ▼                 ▼                            │
          [backend :8080]    /srv 정적 파일 (Vite 빌드 결과)     │
           │        │                                          │
           │        └── /var/run/docker.sock ──> 호스트 Docker ──> 채점 컨테이너 (judge-java / judge-javascript)
           ▼
       [mysql :3306]  (외부에 포트를 열지 않음)
```

- 컨테이너 5개: `caddy`(프론트 정적 파일 + 리버스 프록시 + HTTPS), `backend`(Spring Boot), `mysql`, 모니터링용 `prometheus`·`grafana`(서버 안에서만 접속, 3-5)
- 외부에 열린 포트는 Caddy 의 80·443 뿐이다. backend 8080, mysql 3306 은 compose 내부 네트워크에서만 보인다.
- 채점 컨테이너는 backend 가 호스트 Docker 에 요청해 **형제 컨테이너**로 띄운다.

## 2. 왜 이렇게 했나

### 2-1. 도메인 하나 + 리버스 프록시 (프론트·백엔드 같은 출처)

**결정**: 프론트와 백엔드를 `https://도메인` 하나에 두고, 경로로 나눠 Caddy 가 백엔드로 넘긴다.

**이유: 로그인 방식이 같은 출처를 전제로 한다.**

- 로그인 토큰은 `HttpOnly` + `SameSite=Lax` 쿠키다 (`AccessTokenCookie`). 자바스크립트가 토큰을 읽을 수 없어 XSS 로 토큰을 훔칠 수 없다.
- **CSRF 방어를 `SameSite=Lax` 에 맡기고 CSRF 토큰을 따로 두지 않았다** (`SecurityConfiguration`). 다른 사이트에서 보낸 POST 에는 쿠키가 붙지 않기 때문이다.
- 백엔드에는 CORS 설정이 없다. 같은 출처라 필요 없다.
- 라운지 WebSocket 은 핸드셰이크의 `Origin` 이 `app.frontend-url` 과 같을 때만 받는다 (README 10장).

프론트와 백엔드를 다른 주소에 두면 이 전제가 깨진다. 아래 2-2 에서 자세히 본다.

**같은 출처의 부가 효과**
- 프론트 코드에 백엔드 주소가 없다. API 는 `/api/...`, WebSocket 은 `wss://현재 호스트/ws` 로 부른다. 운영 빌드는 `frontend/.env.production` 의 `VITE_BACKEND_ORIGIN=` (빈 값) 이라 로그인 버튼도 `/oauth2/authorization/google` 로 간다.
- 로컬 개발도 같은 구조다. Vite 개발 서버가 `/api`, `/ws` 를 8080 으로 프록시한다 (`vite.config.ts`). 운영에서는 그 역할을 Caddy 가 한다.

**경로 나누기 주의점**
- `/login/callback` 은 **프론트 화면**(로그인 후 돌아오는 페이지)이고, `/login/oauth2/code/google` 은 **백엔드**(Google 이 인가 코드를 보내는 곳)다. 그래서 `/login/*` 전체가 아니라 `/login/oauth2/*` 만 백엔드로 보낸다.
- Swagger(`/swagger-ui`, `/v3/api-docs`)는 운영에서 일부러 열지 않았다. 필요하면 SSH 터널로 본다.

### 2-2. 왜 프론트를 Vercel 에 따로 두지 않았나

Vercel 은 프론트 배포에 흔히 쓰고 무료지만, 이 프로젝트에서는 얻는 것보다 바꿔야 할 것이 많다.

| 항목 | Vercel(프론트) + 오라클(백엔드) | 오라클 한 대 + Caddy (선택) |
|---|---|---|
| 출처 | `xxx.vercel.app` ↔ `api.도메인` 다른 사이트 | 같은 출처 |
| 로그인 쿠키 | 다른 사이트로 쿠키를 보내려면 `SameSite=None; Secure` 로 바꿔야 함. 그러면 **CSRF 방어가 사라져 CSRF 토큰을 새로 도입**해야 함 | 지금 그대로 (`Lax`) |
| CORS | `allowCredentials` + 허용 출처 설정 필요, 모든 요청에 사전 요청(OPTIONS) | 필요 없음 |
| WebSocket | Vercel 의 rewrite(프록시)는 WebSocket 업그레이드를 넘기지 못한다. 라운지는 결국 백엔드 주소로 직접 붙어야 해서 출처가 갈린다 | 같은 주소 `/ws` |
| 서드파티 쿠키 차단 | 브라우저(사파리 ITP 등)가 다른 사이트 쿠키를 막는 추세라, 커스텀 도메인 없이 `vercel.app` 을 쓰면 로그인이 깨질 위험 | 해당 없음 |
| 배포 단위 | 두 곳 (Vercel 빌드 + 서버 배포), 환경 변수도 두 곳 | `docker compose up` 한 번 |
| 비용 | 둘 다 무료 | 무료 (프론트는 정적 파일 몇 MB, 서버 부담 거의 없음) |
| 얻는 것 | 전 세계 CDN, PR 마다 미리보기 배포 | — |

- Vercel 에 커스텀 도메인(`app.도메인`)을 붙이고 백엔드를 `api.도메인` 에 두면 "같은 사이트(same-site)" 라 `Lax` 쿠키는 붙는다. 하지만 **출처(origin)는 여전히 달라** CORS 설정이 필요하고, WebSocket `Origin` 검사도 바꿔야 한다. 프론트 배포만 위해 인증·보안 설정을 바꾸는 셈이다.
- Vercel 의 장점인 CDN 은 사용자가 대부분 한국이고 서버가 오사카(왕복 약 30ms)라 효과가 작다. 미리보기 배포는 1인 사이드 프로젝트라 필요가 적다.
- 결론: 정적 파일은 이미 떠 있는 Caddy 가 내려주면 추가 비용·설정이 0 이다.

### 2-3. 왜 Caddy 인가 (nginx 대신)

- **HTTPS 자동**: 도메인만 적으면 Let's Encrypt 인증서 발급·갱신, 80→443 리디렉션을 알아서 한다. nginx 는 certbot 설치·갱신 cron·설정 리로드를 따로 챙겨야 한다.
- 설정이 짧다 (`deploy/Caddyfile` 약 20줄). WebSocket 프록시도 별도 설정 없이 된다.
- 프론트 빌드 결과를 Caddy 이미지 안에 넣어(`frontend/Dockerfile`) 프론트 전용 서버(nginx, `vite preview`)를 하나 덜 띄운다.

### 2-4. 채점: 호스트 Docker 소켓을 backend 에 연결

**결정**: backend 컨테이너에 `/var/run/docker.sock` 을 연결해 채점 컨테이너를 호스트 Docker 로 띄운다 (Docker-out-of-Docker).

- 채점 코드(`SandboxRunner`)를 그대로 쓴다. 로컬(Docker Desktop)과 운영이 같은 방식이다.
- Docker 안에 Docker 를 또 띄우는 방식(DinD)은 `--privileged` 가 필요해 더 위험하고 이미지 캐시도 따로 관리해야 한다.

**경로를 호스트와 똑같이 맞춰야 하는 이유**
- `SandboxRunner` 는 제출 코드·입력을 임시 폴더에 쓰고, 그 폴더를 채점 컨테이너의 `/judge/in` 에 **bind mount** 한다.
- bind mount 경로는 **호스트 Docker 가 호스트 파일시스템에서** 찾는다. backend 컨테이너 안의 `/tmp/judge-123` 은 호스트에 없다.
- 그래서 호스트 `/var/lib/coding-test/judge` 를 backend 컨테이너의 **같은 경로**에 연결하고, `-Djava.io.tmpdir=/var/lib/coding-test/judge` 로 임시 폴더를 그 아래에 만들게 했다.

**리눅스에서만 나는 권한 문제 (로컬 운영 구성 점검에서 발견, 수정함)**
- 증상: 운영 구성에서 정답 코드를 제출하면 Java·JavaScript 모두 `CE`. 컴파일 로그는 `file not found: Solution.java`.
- 원인: `Files.createTempDirectory` 는 리눅스에서 폴더를 `700`(만든 사람만 접근)으로 만든다. backend(root)가 만든 입력 폴더를 채점 컨테이너의 `sandbox` 유저(uid 1001)가 읽지 못해 `runner.sh` 의 복사가 실패했다. 윈도우·맥 Docker Desktop 은 bind mount 권한을 무시해서 로컬 개발·`judgeTest` 에서는 드러나지 않았다.
- 수정: `SandboxRunner` 가 입력 폴더를 `755` 로 바꾼다 (`allowSandboxUserToRead`). 수정 후 운영 구성에서 Java·JavaScript 모두 `AC 100` 확인.

**보안 (알고 감수하는 위험)**
- Docker 소켓을 가진 컨테이너는 호스트 root 와 같다. backend 에 원격 코드 실행 취약점이 생기면 서버 전체가 넘어간다.
- 감수한 이유: 1인 사이드 프로젝트, 서버에 다른 서비스·민감 데이터가 없음, backend 는 외부에 포트를 열지 않고 Caddy 뒤에 있음.
- 제출 코드 자체는 채점 컨테이너 안에서 네트워크 차단·읽기 전용·권한 제거·자원 제한으로 격리된다 (README 7장 "컨테이너 격리 옵션"). 소켓은 backend 만 가지고, 채점 컨테이너에는 없다.
- 강화 방법은 4장 "확장" 참고.

### 2-5. 왜 서버 한 대인가 (쿠버네티스·관리형 DB 없이)

- 오라클 Always Free 의 Ampere A1(4 OCPU / 24GB)은 한 대 기준 무료 한도다. 관리형 DB·로드밸런서는 유료이거나 무료 한도가 작다.
- 라운지 상태(접속자 위치, 책상, 관전 방)가 **서버 메모리**에 있다 (`LoungeRegistry`, Spring simple broker). 서버를 여러 대로 늘리려면 이 부분을 먼저 바꿔야 하므로, 지금은 한 대가 자연스럽다.
- 동시 채점 수(`judge.concurrency=4`)는 OCPU 4개에 맞춘 임시값이다. 배포 후 실제 측정으로 조정한다 (`docs/tuning.md`).

### 2-6. 문제 파이프라인은 배포에 포함하지 않는다

문제 수집·변형·검수(`pipeline/`)는 **개발자 PC 에서만** 돌리고, 결과는 Flyway SQL 파일로 레포에 들어간다.

- **LLM 비용**: `claude -p` 를 구독 로그인으로 실행한다. 서버에서 돌리면 API 키(종량제)가 필요하다.
- **저작권**: 원본 지문(`pipeline/workspace/`)이 서버나 공개 레포에 올라가지 않는다.
- **검수 기록**: 문제 추가가 SQL 파일 커밋이라 git 에 이력이 남고, 배포 때 Flyway 가 자동으로 넣는다. 운영 DB 에 직접 INSERT 하는 관리자 화면을 따로 만들 필요가 없다.
- **서버 부담**: 검증 단계에서 채점 컨테이너를 수백 번 띄운다. 운영 서버의 채점 용량을 쓰지 않는다.
- 자세한 설계: `pipeline/README.md`

## 3. 배포 절차

### 3-1. 서버 준비 (한 번만)

1. 오라클 콘솔 Security List 에 TCP 80, 443 인바운드 허용 (22 는 기본 허용)
2. 서버 방화벽(iptables) 에도 80, 443 허용. 오라클 Ubuntu 이미지는 기본으로 막혀 있다.
   규칙은 위에서부터 검사하므로 `REJECT` 줄보다 **앞에** 넣어야 한다. Ubuntu 24.04 이미지(2026-11)는 REJECT 가 5번이라 5번에 넣는다. 넣기 전에 번호를 확인한다
   ```bash
   sudo iptables -L INPUT -n --line-numbers
   sudo iptables -I INPUT 5 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 5 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```
3. Docker 설치 (공식 apt 저장소), `sudo usermod -aG docker ubuntu`
4. 채점 임시 폴더: `sudo mkdir -p /var/lib/coding-test/judge`
5. 레포 clone 후 채점 이미지 빌드 (서버가 arm64 라 서버에서 직접 빌드)
   ```bash
   cd docker/judge
   docker build -f java.Dockerfile -t coding-test/judge-java .
   docker build -f javascript.Dockerfile -t coding-test/judge-javascript .
   ```

### 3-2. 도메인·OAuth

1. 도메인 DNS 에 A 레코드 `도메인 → 서버 공인 IP`
2. Google Cloud Console OAuth 클라이언트의 승인된 리디렉션 URI 에 `https://도메인/login/oauth2/code/google` 추가
3. 대화 기록에 노출된 적이 있는 Google client secret 은 배포 전에 재발급한다

### 3-3. 실행·업데이트

```bash
cp deploy/.env.example .env   # 값 채우기 (SITE_ADDRESS, PUBLIC_URL, 비밀번호, Google, JWT)
docker compose -f compose.prod.yaml --env-file .env up -d --build
docker compose -f compose.prod.yaml logs -f backend
```

- 업데이트: `git pull` 후 같은 명령. 새 Flyway 마이그레이션(새 문제 등)은 backend 시작 때 자동 적용된다.
- `.env` 는 git 제외 대상이다 (루트 `.gitignore`). backend 이미지에도 `backend/.env` 가 들어가지 않게 `backend/.dockerignore` 에서 뺐다.

### 3-4. 로컬에서 운영 구성 점검

같은 compose 를 다른 프로젝트 이름으로 띄워 확인한다. 로컬은 HTTP 라 `SITE_ADDRESS=:80`, `PUBLIC_URL=http://localhost`, `COOKIE_SECURE=false`.

```bash
docker compose -p prodcheck -f compose.prod.yaml --env-file <점검용 env> up -d --build
```

띄운 뒤 `deploy/smoke-test.mjs` 로 제출 채점과 주요 경로를 확인한다. CI(`.github/workflows/ci.yml` 의 `production-smoke`)도 push 마다 같은 점검을 리눅스에서 한다. 채점 테스트(`judgeTest`)만으로는 2-4 의 권한 문제를 못 잡을 수 있어서다: CI 러너 유저의 uid 가 채점 컨테이너의 `sandbox` 와 같은 1001 이면 `700` 폴더도 "주인"으로 읽혀 버린다. 운영 구성에서는 backend 가 컨테이너 안 root 로 돌아 실제 서버와 같은 조건이 된다.

2026-11-17 점검 결과 (Docker Desktop, amd64):

| 항목 | 결과 |
|---|---|
| `/` , SPA 경로(`/problems/7`) | 200 (Caddy 정적 파일, 없는 경로는 index.html) |
| `/api/problems` | 200 (backend 로 프록시) |
| `/oauth2/authorization/google` | 302, `redirect_uri=http://localhost/login/oauth2/code/google` (`:8080` 없이 바깥 주소로 만들어짐 → forward-headers 설정 동작) |
| `/ws` WebSocket 핸드셰이크 | 같은 출처 101, 다른 출처(`Origin: http://evil.example`) 403 |
| 제출 채점 (문제 1, Java·JavaScript) | 둘 다 `AC 100` (권한 수정 후) |

### 3-5. 모니터링 (Prometheus + Grafana)

```
backend ── /actuator/prometheus ◀── 5초마다 가져감 ── [prometheus :9090] ◀── 쿼리 ── [grafana :3001] ── 쿼리 ──▶ mysql (읽기 전용 계정)
```

- **backend**: Micrometer 가 지표를 만들고 Actuator 가 `/actuator/prometheus` 로 내놓는다. Caddy 가 `/actuator` 를 넘기지 않으므로 같은 compose 네트워크의 Prometheus 만 가져갈 수 있다.
- **prometheus**: 지표를 15일 보관한다 (`prometheus-data` 볼륨). 설정은 `monitoring/prometheus/prometheus.yml`.
- **grafana**: 데이터 소스(Prometheus, MySQL)와 대시보드 "코딩 테스트 채점"이 파일로 미리 정의돼 있어 띄우면 바로 보인다 (`monitoring/grafana/`). 대시보드는 JSON 이 원본이라 화면에서 고친 내용은 저장되지 않는다. 패널마다 제목 옆 ⓘ 에 "무엇 / 정상 / 이상 신호 / 출처" 설명이 있다.
- **외부 노출 없음**: 9090, 3001 모두 서버의 `127.0.0.1` 에만 연다. Security List·iptables 에 열 필요가 없다.

**처음 한 번**

1. `.env` 에 `GRAFANA_ADMIN_PASSWORD`, `GRAFANA_MYSQL_PASSWORD` 를 채운다 (`deploy/.env.example`). 영문·숫자만 쓴다(SQL 에 그대로 들어간다): `openssl rand -hex 24`
2. `docker compose -f compose.prod.yaml --env-file .env up -d` (mysql 은 새 환경 변수 때문에 한 번 다시 만들어진다. 데이터는 볼륨에 그대로 있다)
3. 이미 데이터가 있는 MySQL 이라 초기화 스크립트가 자동으로 돌지 않으므로, 읽기 전용 계정을 직접 만든다. 같은 명령을 다시 실행하면 비밀번호만 다시 맞춘다
   ```bash
   docker compose -f compose.prod.yaml --env-file .env exec mysql sh /docker-entrypoint-initdb.d/create-grafana-reader.sh
   ```

**보기 (내 PC 에서)**

```bash
ssh -N -L 3001:127.0.0.1:3001 -L 9090:127.0.0.1:9090 ubuntu@<서버 IP>
```

터널을 연 채로 브라우저에서 `http://localhost:3001` (admin / `GRAFANA_ADMIN_PASSWORD`). Prometheus 화면은 `http://localhost:9090` → Status → Targets 에서 backend 가 UP 인지 본다.
로컬 개발(`compose.yaml`)도 같은 설정 파일을 쓴다. `docker compose up -d` 후 백엔드를 `bootRun` 으로 띄우면 `http://localhost:3001` (admin / admin) 에서 같은 대시보드가 보인다.

## 4. 나중에 확장하는 방법

부하가 생기는 순서대로 적었다. 측정 없이 미리 하지 않는다.

### 4-1. 채점이 밀릴 때 (가장 먼저 올 병목)

1. **동시 채점 수 조정**: `judge.concurrency`, 대기열 상한을 측정값으로 맞춘다 (`./gradlew concurrencyTest`, `docs/tuning.md`).
2. **채점 서버 분리**: 채점을 backend 밖의 작업자(worker)로 옮긴다. backend 는 제출을 메시지 큐(RabbitMQ, Redis Stream 등)에 넣고, 채점 전용 서버 여러 대가 꺼내 처리한다. 지금 구조(접수 → `PENDING` 저장 → 작업 스레드가 채점)가 이미 비동기라, 작업 스레드 부분을 큐 소비자로 바꾸면 된다 (README 2장 "비동기 채점 방식").
   - 이렇게 하면 Docker 소켓도 backend 에서 빠지고 채점 서버에만 남아 2-4 의 위험이 줄어든다.
3. **격리 강화**: 채점 서버에 gVisor(`runsc`) 런타임이나 rootless Docker 를 적용한다 (README 7장 "알려진 한계와 향후 강화").

### 4-2. 라운지 접속자가 많아질 때 (backend 여러 대)

- 라운지 상태가 서버 메모리에 있어서, backend 를 두 대로 늘리면 서로 다른 서버에 붙은 사람끼리 안 보인다.
- 바꿀 것: Spring simple broker → **외부 STOMP 브로커**(RabbitMQ STOMP 플러그인 relay), `LoungeRegistry`·`DeskRooms` → Redis 같은 공유 저장소.
- 로그인은 JWT 쿠키라 서버 상태가 없어 그대로 둬도 된다.
- Caddy 는 `reverse_proxy backend1:8080 backend2:8080` 처럼 여러 대로 나눌 수 있다.

### 4-3. 정적 파일·트래픽

- **Cloudflare 프록시**(무료)를 도메인 앞에 두면 정적 파일 CDN 캐시, DDoS 완화를 같은 출처 그대로 얻는다. Vercel 로 옮기는 것보다 바꿀 것이 적다 (WebSocket 도 지원).
- 프론트를 Vercel·Cloudflare Pages 로 옮기고 싶다면, CDN 단에서 `/api`, `/ws`, `/oauth2` 를 오라클로 넘기는 방식으로 **같은 출처를 유지**해야 2-2 의 문제가 생기지 않는다.

### 4-4. DB

- 우선 **백업**: `mysqldump` 를 cron 으로 매일 돌려 오라클 Object Storage(무료 20GB)에 올린다. (배포 직후 해야 할 일)
- 테스트 케이스 JSON 이 커지면 `test_cases` 를 별도 저장소(Object Storage)로 빼는 것을 검토한다 (README 8장 "JSON 컬럼 판단 기준").

### 4-5. 배포 자동화

- 지금은 SSH 로 접속해 `git pull` + `compose up`. 배포가 잦아지면 GitHub Actions 에서 arm64 이미지를 빌드해 GHCR 에 올리고, 서버는 `docker compose pull` 만 하게 바꾼다. 서버에서 Gradle 빌드를 하지 않아도 되어 배포 시간이 줄어든다.
