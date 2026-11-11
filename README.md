# coding-test — 픽셀 메타버스 모각코 + 코딩테스트 채점

접속한 사람 모두가 하나의 공용 픽셀 공간에 모여 각자 책상에 앉아 코딩테스트 문제를 푸는 "온라인 모각코" 서비스.
혼자 공부할 때의 막막함을 줄이는 것이 목표이며, **다른 사람이 무엇을 풀고 있는지 보이고 지켜볼 수 있다**는 점이 핵심이다.

레퍼런스: 프로그래머스(문제 풀이·채점) + ZEP(2D 메타버스 공간)

---

## 1. 문제 상황

### 1-1. 화상/메타버스 툴(ZEP, Discord)의 개발 비적합성
- **화질 깨짐 및 텍스트 복사 불가**: WebRTC 화면 공유 기반이라 코드가 뭉개지고, 관전자가 코드를 드래그·복사할 수 없다.
- **높은 자원 점유율**: 화상 스트리밍이 메모리·대역폭을 많이 잡아먹어 IDE와 함께 켜면 렉이 발생한다.
- **수동 상태 변경**: 풀이 중인지 쉬는 중인지 아바타 상태를 매번 직접 바꿔야 한다.

### 1-2. 혼자 푸는 환경의 고독감과 플랫폼 파편화
- **학습 지속성 저하**: 백준/프로그래머스는 완전한 1인 환경이라 동기부여가 어렵고 중도 포기율이 높다.
- **작업 흐름 분리**: 가상 공간을 켜 두어도 코딩·채점은 다른 탭에서 이루어져 공간과 실제 개발 행위가 겉돈다.

### 1-3. 코드 피드백의 높은 피로도
- "상단 반복문 3번째 줄 변수명 틀렸어"처럼 채팅·음성으로 일일이 설명해야 해 피드백 효율이 낮다.

### 해결 방식
**코딩 행위 자체가 가상 공간의 데이터로 즉시 변환되는 개발자 특화 아케이드.**
코드는 영상이 아닌 텍스트(CRDT)로 동기화하고, 타이핑·채점 결과는 아바타 상태와 이펙트로 자동 반영한다.

---

## 2. 결정 사항

| 항목 | 결정 |
|---|---|
| 사용자 식별 | 게스트로 시작(localStorage의 임시 UUID + `익명_XXXX` 닉네임). 메인 화면에서 직접 로그인도 가능 |
| 로그인 요구 시점 | **[코드 실행]**(예제만 실행), **[제출 및 채점]**, **[1:1 페어 요청]** 시 Google OAuth 팝업 (Lazy Auth). 채점 서버를 쓰는 동작은 모두 로그인 필요 |
| 로그인 방식 | Google 로그인 성공 → `users`를 Google 계정 ID(`sub`)로 찾거나 생성 → 우리 서버 JWT 발급. 토큰은 `Authorization: Bearer` 헤더(쿠키 아님: CSRF 대응 불필요, Swagger·STOMP에서 쓰기 쉬움). HS256 서명(서버 하나라 비밀키 하나로 충분). 액세스 토큰 24시간, 리프레시 토큰 없음(만료 시 재로그인, 보안 강화 때 도입). 로그인 과정의 세션은 Google state 검증에만 쓰고 바로 폐기 |
| 첫 로그인 닉네임 | `사용자_XXXXXX` 자동 생성 (겹치면 다시 생성, Google 실명 노출 방지). 닉네임 변경 API는 나중에 |
| 비밀값 | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET`(32바이트 이상)은 환경 변수로만 받음. 공개 레포라 레포에 넣지 않으며, 없으면 서버가 시작하지 않음 |
| 비로그인 허용 영역 | 맵 입장, 아바타 이동, 문제 지문 조회, 에디터에서 코드 작성해 보기 |
| 채점 방식 | Docker 샌드박스 직접 구현 |
| 풀이 지원 언어 | **Java, JavaScript** |
| 채점 자원 제한 | 문제별 설정, 미설정 시 기본값: 실행 테스트당 3초 / 메모리 265MB(컨테이너 전체, 최소 128MB). 컴파일 10초, CPU 1코어 고정 |
| 채점 결과 종류 | AC(정답), WA(오답), TLE(시간 초과), MLE(메모리 초과), RE(런타임 에러), CE(컴파일 에러) |
| 채점 방식 | **프로그래머스 방식**: `solution` 함수 작성, 테스트 전부 실행 후 테스트별 결과 + 점수(통과 수/전체 × 100) |
| 테스트 공개 | 예제는 지문에 공개, 히든 테스트는 결과만 표시 |
| 실행 범위 | **[코드 실행]** = 예제 테스트만 (입력·기댓값·반환값·유저 출력 표시) / **[제출]** = 예제 + 히든 전부, 점수 계산 (히든은 판정·시간·메모리만) |
| 반환값 비교 | 정수·문자열·불리언은 정확히, 실수는 절대 또는 상대 오차 1e-6까지 같다고 봄, 배열은 원소별 비교 |
| 문제 저장 | MySQL. seed 문제 5개는 Flyway `V2__insert_seed_problems.sql`로 넣음 (처음 만든 JSON 파일에서 변환). 프로그래머스 유형을 변형해 지문은 직접 작성 |
| DB 접근 | **Spring Data JPA**. 테이블은 Flyway SQL이 만들고 JPA는 `ddl-auto=validate`로 엔티티와 테이블이 맞는지만 확인 |
| 로컬 DB / 테스트 DB | 로컬은 Docker Compose(`compose.yaml`, MySQL 8.4, 호스트 포트 3307: 직접 설치한 MySQL이 3306을 써서), 테스트는 Testcontainers로 실제 MySQL 컨테이너 (H2는 JSON 컬럼·CHECK 동작이 달라 제외) |
| 시작 코드 | 서버가 문제 상세 응답에 언어별 `starterCode`로 제공 (래퍼 규칙과 한곳에서 관리, 모든 문제의 시작 코드가 채점기에서 실행되는지 테스트로 확인) |
| API 확인 | Swagger UI (`/swagger-ui.html`). 운영 배포 때는 끔 |
| 제출 API | 로그인(7단계)과 함께 만듦. 5단계는 문제 조회 + [코드 실행]만 |
| 실행 기록 저장 | [코드 실행]도 저장 (`runs` + `run_test_results`). 제출 테이블에 `mode`로 합치지 않고 분리: 나중에 유저가 직접 추가한 테스트 입력으로 실행하는 기능을 넣으면 `test_cases`를 가리키지 않는 결과가 생기고, 합치면 제출 조회마다 `mode` 조건 누락 시 예제 AC가 푼 문제로 기록되는 버그 위험·실행 행이 대부분을 차지하는 문제가 있음 |
| 비동기 채점 방식 | [제출]과 [코드 실행] 모두 비동기 (동기면 대기열이 생긴 뒤 HTTP 시간 제한에 걸릴 수 있음). **서버 안의 작업 스레드**: 제출을 `PENDING`으로 저장 → 커밋 후 이벤트(`@TransactionalEventListener(AFTER_COMMIT)`)로 가상 스레드 실행기에 채점을 넘기고 바로 202 응답 → 작업 스레드가 `JUDGING` → `DONE`/`FAILED`. 서버 시작 시 남은 `PENDING`·`JUDGING`을 다시 채점. DB 대기열(반복 조회 + `FOR UPDATE SKIP LOCKED`)은 서버 1대에선 이점이 작아 제외, 서버 여러 대가 되면 메시지 큐로 이동 |
| 개발 순서 | API보다 **DB 구조를 먼저** 설계 (비동기 채점 상태를 저장해야 해서 API 형태가 DB에 따라 정해짐) |
| DB에 저장하는 것 | `users`, `problems`, `test_cases`, `user_solved_problems`(푼 문제, 별도 테이블), `submissions` + `submission_test_results`(제출·비동기 채점 상태. 새로고침·서버 재시작·채점 오류 조사 대응), `runs` + `run_test_results`(실행 기록) |
| DB에 저장하지 않는 것 | 게스트 정보(localStorage), 아바타 위치·상태·관전·페어 세션·Yjs 문서(서버 메모리 실시간), 페어 채팅(저장 안 함), 작성 중인 코드(일단 localStorage) |
| 제출 내역 화면 | MVP 제외 (데이터는 저장) |
| 파라미터 저장 | `problem_parameters` 테이블로 분리. 이름 중복·타입 제한을 DB가 검사하게 하려는 것 (문제당 1~3행이라 비용 거의 없음). 테스트 입력·정답은 통째로 쓰는 값이라 JSON 컬럼 유지 (판단 기준은 8장) |
| 언어별 시간 | 같은 제한으로 시작, 검증용 정답 코드로 측정 후 필요하면 배수 도입 |
| 문제 출처 | 타 사이트 문제를 AI로 변형·검수 후 운영자가 직접 등록 (MVP는 seed 데이터) |
| 관전 거부 | MVP에서 만들지 않음 |
| 아바타 이펙트 상세 | 미정 (FR-02-4와 UC-01 내용이 다름, 추후 확정) |
| 에디터 배치 | **분할 화면, 프로그래머스 배치**: 착석하면 맵이 왼쪽 패널로 줄고, 왼쪽 지문 / 오른쪽 위 에디터 / 아래 실행·채점 결과 (지문 위 탭 없음, 문제는 문제 바의 `다른 문제`로 바꿈) / 하단 `초기화`·`코드 실행`·`제출 후 채점하기`. 맵 패널은 48px 활동 띠로 접기 가능 (모달은 풀이 중 공간이 안 보여서 제외). 상세는 `docs/ui-design.md` |
| UI 테마 | hallmark 디자인 스킬의 Squeeze 테마 기반: **따뜻한 차콜 바탕**(초록은 가독성 문제로 제외), 패널은 6px로 각지게 하고 버튼·칩·말풍선·이름표만 둥글게, 에디터는 가장 깊은 차콜, 복숭아색은 버튼에만. 글꼴은 Gabarito·Manrope(한글 Jua·고운돋움), 코드는 JetBrains Mono. 픽셀은 맵 안에만 |
| 캐릭터 | **동글한 픽셀 고양이** 아바타(털 무늬 5종). 타이핑 중엔 앞발로 키보드를 두드림, 자리 비움은 낮잠 |
| 맵 표현·에셋 | 2D 탑다운 픽셀(Three.js 3D 제외), CC0 에셋 팩(Kenney 등) |

### 권한

쓰기만 제한하고 읽기는 전부 허용한다.

| 기능 | 주인 | 페어 | 관전자 |
|---|---|---|---|
| 코드 보기 | O | O | O |
| 코드 수정 | O | O | X |
| 제출 | O | X | X |
| 페어 채팅 | O | O | X |

---

## 3. 기능 요구사항 (MVP)

### FR-01 회원 인증 및 사용자 관리
| ID | 기능 | 요구사항 |
|---|---|---|
| FR-01-1 | 게스트 진입 | 로그인 없이 접속 시 localStorage에 UUID와 `익명_XXXX` 닉네임을 생성해 세션 유지 |
| FR-01-2 | 지연 소셜 로그인 | 제출 또는 1:1 페어 요청 시점에 Google OAuth 2.0 팝업 호출 |
| FR-01-3 | 인증 상태 동기화 | 로그인 성공 시 JWT 저장, 웹소켓으로 아바타 식별자·닉네임을 게스트 → 회원으로 실시간 업그레이드 |
| FR-01-4 | 풀이 기록 | 로그인 유저만 문제별 `is_solved`와 `Total Solved` 저장 |

### FR-02 메타버스 맵 및 실시간 아바타 인터랙션
| ID | 기능 | 요구사항 |
|---|---|---|
| FR-02-1 | 단일 픽셀 라운지 | Phaser.js/Canvas 2D 픽셀 맵, 방향키/WASD 이동 |
| FR-02-2 | 위치/상태 동기화 | WebSocket(STOMP)으로 같은 맵 접속자의 좌표·동작 상태를 저지연 브로드캐스트 |
| FR-02-3 | 오브젝트 감지 | 책상/터미널 인접 시 `[F] 책상에 앉기` / `[F] OO님 관전/페어 요청` 툴팁 |
| FR-02-4 | 시각 이펙트 | 타이핑 중: 고양이 앞발이 키보드를 두드리고 말풍선 / AC: `SOLVED!` 팝업 + 폭죽 + 전광판 자막 / 오답·초과: 엎드림 + 버그 몬스터 (상세 미정) |

### FR-03 에디터 및 문제 은행
| ID | 기능 | 요구사항 |
|---|---|---|
| FR-03-1 | Monaco Editor 분할 화면 | `F` 착석 시 맵이 왼쪽 패널로 줄고(접기 가능) 프로그래머스 배치로 지문·에디터·결과 표시, 언어(Java/JavaScript) 선택 |
| FR-03-2 | 문제 은행 조회 | seed된 문제 목록 조회, 로그인 유저가 통과한 문제에 `[완료]` 뱃지 |
| FR-03-3 | 제출 및 결과 출력 | 비동기 채점 후 결과(AC/WA/TLE/MLE/RE/CE)와 실행 시간·메모리를 에디터 하단 터미널에 출력 |
| FR-03-4 | 코드 관전 | 실시간으로 작성 중인 코드를 관전 |

### FR-04 1:1 페어 프로그래밍
| ID | 기능 | 요구사항 |
|---|---|---|
| FR-04-1 | 세션 형성 | 타 유저 책상에서 `[F]`로 요청, 풀이자가 수락하면 결합 (최대 2명). 제출은 주인만 |
| FR-04-2 | 실시간 동시 편집 | Yjs(CRDT) + `y-monaco`로 커서·선택 영역·텍스트 동기화 |
| FR-04-3 | 프라이빗 채팅 | 에디터 우측 사이드바, 페어 두 사람만 보는 채팅 |

### FR-05 Docker 기반 비동기 격리 채점
| ID | 기능 | 요구사항 |
|---|---|---|
| FR-05-1 | 비동기 채점 | 제출 수신 시 비동기 작업으로 채점 워커에 할당 |
| FR-05-2 | 샌드박스 격리 | 제출 코드를 샌드박스 컨테이너에서 컴파일·실행, 시스템 접근 차단 |
| FR-05-3 | 자원 제한 | 실행 3초(컴파일 10초 별도), 메모리 265MB, CPU 1코어 |
| FR-05-4 | 테스트케이스 검증 | 등록된 입출력 세트와 실행 결과를 비교해 판정 |

### 유스케이스
- **UC-01 자율 풀이 및 제출**: 빈 책상 → `F`로 에디터 → 문제 선택(완료 뱃지 확인) → 작성 중 `[타이핑 중]` 상태 노출 → 제출 → 비동기 채점 → AC면 축하 이펙트 + 완료 상태·Total Solved 1 증가 / 오답·초과면 실패 이펙트 + 에디터에 피드백, 기록 불변.
- **UC-02 페어 프로그래밍**: 유저 2가 유저 1에게 페어 요청 → 유저 1 수락 → 같은 Monaco Editor에서 각자의 커서·이름표로 동시 편집 → 측면 1:1 채팅으로 피드백.

### MVP 제외 범위
MVP 이후 공용 맵·관전 구조 위에 확장한다 (MVP 설계 변경 불필요).
- 관리자 백오피스 전체(FR-06): 문제 직접 등록, Markdown 지문 에디터, 테스트케이스 관리, 문제 상태(Draft → Testing → Published → Archived), AI 지문 변형·테스트케이스·정답 코드 생성, 2-Step 검수(Dry-run 자동 채점 + 수동 승인), UC-03
- 스터디룸(링크 전용 맵), 근접 채팅·음성·화상, 라이브 코딩(면접관/지원자) 모드, 손들기, 채널 분할
- 오늘 푼 문제 수·스트릭·랭킹, 아바타·맵 꾸미기, AI 힌트, 레벨업

---

## 4. 기술 스택과 선택 이유

> 백엔드/프론트엔드 개발 언어와 채점에서 지원하는 풀이 언어(Java, JavaScript)는 별개다.

| 영역 | 선택 | 이유 |
|---|---|---|
| 언어 | **Java 25 (LTS)** | 가상 스레드의 `synchronized` pinning 문제가 JDK 24(JEP 491)에서 해결됨. 채점 워커가 Docker 호출에서 오래 블로킹되고 웹소켓 연결이 많은 이 서비스에 직접 이득. 21보다 지원 기간도 길다 |
| 프레임워크 | **Spring Boot 4.1.2** | 3.x 마지막(3.5)은 무료 지원 종료. 4.x는 Spring Framework 7 / Jakarta EE 11 / Jackson 3 기반 |
| 빌드 | **Gradle 9 + Kotlin DSL** | 빌드 설정이 타입 검사되어 오타를 IDE가 바로 잡아 주고 자동완성이 된다. Gradle 신규 프로젝트 기본값 |
| DB | **MySQL 8.4** + **Flyway 12** | 관계(FK)·트랜잭션·집계가 중심인 데이터라 RDB. 스키마와 seed 데이터를 번호 붙은 SQL 파일로 관리해 로컬·테스트·배포 DB가 같은 순서로 같은 변경을 받음. CHECK·JSON·복합 PK는 엔티티보다 SQL로 정확히 표현 |
| DB 접근 | **Spring Data JPA** (Hibernate 7) | 고정 조회가 대부분이라 Repository 메서드로 충분. `@ManyToOne`은 항상 LAZY (관계를 따라 연쇄 조회가 퍼지는 것을 막고, 같이 읽을 때만 fetch join / `@EntityGraph`) |
| QueryDSL | **MVP 제외** | MVP 조회는 고정 쿼리라 Spring Data JPA/JPQL로 충분. 동적 필터가 생기면 관리되는 `io.github.openfeign.querydsl` 포크로 도입 |
| 실시간 | STOMP (Spring simple broker) | (예정) 아바타 위치·상태·이펙트·채점 결과 알림 브로드캐스트 |
| 동시 편집 | Yjs + `y-monaco` | (예정) 병합은 클라이언트 CRDT가 하고 서버는 중계만. 중계 위치(Spring 릴레이 vs Node `y-websocket`)는 페어 기능 구현 때 결정 |
| 인증 | **Spring Security 7** OAuth2 Client(Google 로그인) + OAuth2 Resource Server(JWT 검증, 내장 Nimbus) | 별도 JWT 라이브러리(jjwt) 없이 발급·검증 |
| 채점 | **Docker + docker-java 3.7.1** | 제출마다 격리 컨테이너 생성. CLI 호출보다 종료 코드·OOM 등을 구조화된 값으로 받을 수 있다 |
| API 문서 | **springdoc-openapi 3.2.0** (Swagger UI) | 컨트롤러에서 API 문서가 자동으로 만들어지고 브라우저에서 바로 요청을 보내 볼 수 있음. Spring Boot 4.1.2 기준 빌드 |
| 테스트 | JUnit 5, AssertJ, **Testcontainers 2** | 샌드박스와 DB는 실제 Docker 컨테이너를 띄우는 통합 테스트(`judgeTest`) |
| 기타 | Lombok(JPA 엔티티 한정), DTO는 `record` | |
| 프론트엔드 | React, Phaser.js, Monaco Editor | (예정) 맵은 2D 탑다운이라 타일맵·충돌·입력이 내장된 Phaser 추천(3D·PixiJS 비교는 `docs/ui-design.md` 5장). 버전·React 연결 방식은 구현 전 검토 |

---

## 5. 레포 구조

```
coding-test/
├─ README.md
├─ compose.yaml                  로컬 MySQL 8.4
├─ docs/erd.dbml                 ERD (dbdiagram.io에 붙여 넣어 보기)
├─ docs/ui-design.md             UI 설계 (화면·레이아웃·테마 토큰·이펙트·맵 기술 검토)
├─ backend/                      Spring Boot (Gradle Kotlin DSL)
│  ├─ src/main/java/com/codingtest/
│  │  ├─ auth/                   보안 설정, Google 로그인 성공 처리, JWT 발급(TokenService)
│  │  ├─ user/                   회원 엔티티, 내 정보 API
│  │  ├─ judge/                  채점 샌드박스 실행(SandboxRunner) + 판정기(Judge) + 작업 스레드 실행기·재시작 복구·공통 응답
│  │  ├─ submission/             [제출] API, 엔티티, 작업 스레드 채점(SubmissionJudgeWorker)
│  │  ├─ problem/                문제 엔티티(Problem, ProblemParameter, TestCase), Repository, 문제 조회 API, 시작 코드(StarterCode)
│  │  └─ run/                    [코드 실행] API, 엔티티, 작업 스레드 채점(RunJudgeWorker)
│  ├─ src/main/resources/db/migration/   Flyway: V1 테이블 생성, V2 seed 문제 5개
│  └─ src/test/resources/problems/{id}-{slug}/   검증용 코드: accepted(AC) / wrong(WA) / slow(TLE) .java .js
├─ frontend/                     React (예정)
└─ docker/
   └─ judge/                     채점 이미지
      ├─ java.Dockerfile         → coding-test/judge-java       (eclipse-temurin:25-jdk-noble)
      ├─ javascript.Dockerfile   → coding-test/judge-javascript (node:26-bookworm-slim)
      ├─ runner.sh               컨테이너 진입점: 컴파일 → 테스트별 실행 → 결과 tar 출력
      ├─ language/               언어별 소스 파일명·컴파일·실행 명령
      └─ wrapper/                언어별 래퍼 (유저의 solution 을 호출하는 고정 진입점)
```

---

## 6. 로컬 실행

필요: JDK 25, Docker Desktop(실행 중)

```bash
docker compose up -d    # 로컬 MySQL (localhost:3307, DB·계정·비밀번호 모두 coding_test)
cd backend
./gradlew test          # 일반 테스트 (Docker 불필요)
./gradlew judgeTest     # 채점 이미지 빌드 + 실제 컨테이너로 샌드박스·DB 테스트 (MySQL은 Testcontainers가 따로 띄움)
./gradlew concurrencyTest   # 제출 10건 동시 채점 측정 (무거워서 judgeTest와 분리)
./gradlew buildJudgeImages   # 채점 이미지만 빌드
./gradlew bootRun       # 서버 실행 (시작할 때 Flyway가 테이블과 seed 문제를 만듦, 아래 환경 변수 필요)
```

`bootRun` 전에 비밀값 세 개를 `backend/.env`(git 제외 대상)에 적는다. 환경 변수로 넣어도 된다. 테스트는 Gradle이 가짜 값을 넣어 줘서 필요 없다.

```
GOOGLE_CLIENT_ID=...        # Google Cloud Console에서 만든 OAuth 클라이언트
GOOGLE_CLIENT_SECRET=...
JWT_SECRET=...              # 32바이트 이상 아무 값 (예: openssl rand -base64 48)
```

Google OAuth 클라이언트의 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google`.

토큰 받기: 브라우저에서 http://localhost:8080/oauth2/authorization/google → Google 로그인 → `{"accessToken": "..."}` 화면의 값을 복사 → Swagger 오른쪽 위 **Authorize**에 붙여 넣기.

서버를 켠 뒤 http://localhost:8080/swagger-ui.html 에서 API를 확인한다.

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/api/problems` | 문제 목록 (`id`, `title`, `level`) |
| GET | `/api/problems/{id}` | 문제 상세 (지문, 파라미터, 반환 타입, 시간·메모리 제한, 언어별 `starterCode`) |
| POST | `/api/problems/{id}/runs` | [코드 실행] `{"language": "JAVA", "sourceCode": "..."}` → 예제만 채점. 접수만 하고 `202 {"id", "status": "PENDING"}`. **로그인 필요** |
| GET | `/api/runs/{id}` | 실행 결과 (상태, 판정, 통과 수, 예제별 입력·기댓값·반환값·출력). `status`가 `DONE`이 될 때까지 다시 부른다. 본인 것만 |
| POST | `/api/problems/{id}/submissions` | [제출] 예제 + 히든 전부 채점. 접수만 하고 `202`. **로그인 필요** |
| GET | `/api/submissions/{id}` | 제출 결과 (상태, 판정, 점수, 테스트별 결과. 히든은 판정·시간·메모리만). 본인 것만 (남의 것은 404) |
| GET | `/api/users/me` | 내 정보 (`id`, `nickname`, `email`, `totalSolved`). **로그인 필요** |
| GET | `/oauth2/authorization/google` | Google 로그인 시작 → 성공하면 `{"accessToken": "..."}` |

에러는 표준 형식(RFC 9457 `ProblemDetail`): 없는 문제 404, 빈 코드·64K자 초과·지원하지 않는 언어 400. 로그인이 필요한 API에 토큰이 없거나 틀리면 401 (Google 로그인 화면으로 보내지 않음).
아직 동시 실행 수 제한이 없어 접수되는 대로 바로 컨테이너를 띄운다 (7단계에서 대기열 추가). 비동기 채점의 동작과 선택 이유는 7장 "비동기 채점".

---

## 7. 채점 샌드박스 설계

### 동작 흐름
1. 백엔드(`SandboxRunner`)가 임시 폴더에 유저 코드(`Solution.java` / `solution.js`)와 `tests/1.in, 2.in, ...`을 쓴다. 각 입력 파일은 `solution`에 넘길 **인자 JSON 배열** 한 줄이다 (예: `[[1,1,1,1,1], 3]`).
2. 격리 옵션을 건 컨테이너를 만들고, 임시 폴더를 `/judge/in`에 **읽기 전용**으로 연결한다.
3. 컨테이너 안 `runner.sh`(root)가 유저 코드를 `sandbox` 유저(uid 1001)로 내려 컴파일(10초) → 테스트별 실행(3초)한다.
4. 테스트마다 래퍼가 인자를 변환해 `solution`을 호출하고 `{"returnValue":...,"userOutput":"..."}` 한 줄을 출력한다. 이 출력·에러(64KB까지)·종료 코드·실행 시간·최대 메모리·OOM 여부를 root 전용 `/tmp/result`(권한 700)에 기록한다.
5. 프로그래머스처럼 **실패한 테스트가 있어도 모든 테스트를 실행한다** (테스트별 결과·점수 표시를 위해).
6. 결과 폴더를 tar로 묶어 컨테이너 stdout으로 내보내고, 백엔드가 이를 받아 `SandboxResult`로 변환한다. 정상 종료한 테스트는 `returnValue`(JsonNode)와 `userOutput`을 파싱해 담고, 그 외에는 `returnValue = null`.
7. **정답 비교(AC/WA 판정)는 컨테이너 밖에서 한다.** 정답 파일을 컨테이너에 넣으면 유저 코드가 읽어서 출력할 수 있기 때문이다. (판정 로직은 다음 단계)

### 래퍼 (`docker/judge/wrapper/`)
유저는 `main` 없이 `solution`만 작성하고, `main`은 이미지에 고정된 래퍼가 맡는다 (프로그래머스 방식).
| 언어 | 래퍼 | 동작 |
|---|---|---|
| Java | `Main.java` + `JudgeJson.java` | 리플렉션으로 `Solution.solution`의 파라미터 타입을 읽어 JSON을 변환·호출. `System.out`을 가로채 `userOutput`으로 모음. 외부 라이브러리 없이 자체 JSON 파서 사용 |
| JavaScript | `main.js` | `vm.compileFunction`으로 `solution.js`를 함수 본문으로 컴파일 → `solution` 꺼내 호출 (`function`/`const` 둘 다 가능). `process.stdout.write`를 가로챔 |

- 지원 타입: int, long, double, boolean, String 과 이들의 배열(int[], long[], String[], int[][] 등 다차원 포함). 그 외 타입은 런타임 에러.
- 에러 메시지·줄 번호는 유저 파일 기준(`Solution.java:4`, `solution.js:2`)으로 나온다.
- `userOutput`은 64KB까지만 모은다.
- 한계: JS는 2^53보다 큰 long을 정확히 표현하지 못한다. 유저 코드가 래퍼를 우회해 결과 JSON을 직접 출력할 수는 있지만, 그러려면 정답을 계산해야 하므로 이득이 없다.

### 판정 규칙 (`Judge`)
| 순서 | 조건 | 판정 |
|---|---|---|
| 0 | 컴파일 실패 | CE (테스트 실행 안 함) |
| 1 | 커널 OOM 강제 종료, 또는 stderr에 `java.lang.OutOfMemoryError` / `heap out of memory` | MLE (JS는 힙 부족 후 시간 제한까지 남아 있으므로 TLE보다 먼저 봄) |
| 2 | 종료 코드 137 (시간 제한 강제 종료) | TLE |
| 3 | 그 외 비정상 종료 | RE |
| 4 | 정상 종료 + 반환값이 정답과 같음 / 다름 | AC / WA |

전체 판정은 모두 AC면 AC, 아니면 처음 실패한 테스트의 판정. 점수 = 통과 수 / 전체 × 100 (소수 첫째 자리 반올림).
문제 생성용 테스트 정답값은 스크립트로 계산해 넣었고, `ProblemSolutionsTest`가 검증용 코드로 정답 데이터와 판정기를 함께 확인한다.

### 컨테이너 격리 옵션
| 옵션 | 목적 |
|---|---|
| `--network none` | 외부 통신 차단 |
| `--read-only` + `--tmpfs /tmp:size=32m,noexec` | 쓰기 가능한 곳은 32MB 메모리 디스크뿐 |
| `--memory=265m --memory-swap=265m` | 메모리 상한, 스왑 금지 |
| `--cpus=1.0` | CPU 1코어 |
| `--pids-limit=128` | fork bomb 방지 |
| `--ulimit core=0` | 비정상 종료 시 코어 덤프 금지 |
| `--cap-drop ALL --cap-add SETUID,SETGID,KILL` | root 권한 제거, 유저 전환·프로세스 정리에 필요한 것만 허용 |
| `--security-opt no-new-privileges` | 실행 중 권한 재획득 금지 |

문제별 제한은 `problems.time_limit_ms`·`memory_limit_mb`(DB 기본값 3000ms, 265MB)를 `ExecutionLimits(timeLimit, memoryLimitMb)`로 넘긴다. 컨테이너에는 환경변수 `RUN_TIMEOUT`, `MEMORY_LIMIT_MB`로 전달된다.
힙 크기 = 메모리 제한 − 55MB(런타임 자체 사용분). Java는 `javac`/`java` 모두 `-Xmx<힙>m`(+ `-Xss64m -XX:+UseSerialGC`), JavaScript는 `--max-old-space-size=<힙>`. 힙을 컨테이너 한도보다 낮춰야 커널 강제 종료 대신 언어 차원의 메모리 에러로 끝난다. JS의 "컴파일"은 `node --check` 문법 검사.

### 검증된 동작 (`SandboxRunnerTest`, 22개)
- 래퍼: 9개 타입 JSON 왕복, 유저 출력 분리, `solution` 없음, 미지원 파라미터·반환 타입 (Java/JS), JS 화살표 함수
- Java: 정상 반환, 무한 루프(테스트마다 3초 강제 종료), `OutOfMemoryError`, 런타임 예외, 컴파일 에러, 깊이 20만 재귀 — 실패해도 모든 테스트 실행
- 문제별 제한: 시간 1초 제한에서 1.5초 코드 강제 종료 / 메모리 128MB 제한에서 120MB 할당 실패 (기본 제한에서는 둘 다 통과)
- Java 탈출 시도 차단: 네트워크, 결과 파일 조작, 결과 전송 통로(fd 3) 위조, 채점 프로세스(`/proc/1`) 접근, 시스템 파일 쓰기, 프로세스 무한 생성
- JavaScript: 정상 반환, 무한 루프, 힙 부족, 런타임 예외, 문법 에러

### 알려진 한계와 향후 강화
- **커널 공유**: 컨테이너는 호스트 커널을 공유하므로 커널/런타임 취약점에 노출될 수 있다. 배포 시 gVisor(`--runtime=runsc`) 적용, 채점 서버를 DB·메인 백엔드와 분리, 커널·Docker 업데이트 유지.
- **컨테이너 root = 호스트 root**: runner가 root로 돈다. user namespace remap으로 강화 가능.
- **테스트 입력 노출**: 유저 코드는 테스트 입력을 읽을 수 있다. 히든 테스트의 stderr를 화면에 그대로 보여주지 말 것.
- **JS 메모리 초과 지연**: 힙 부족 에러는 즉시 찍히지만 프로세스가 3초 제한까지 남는다. 판정은 stderr로 가능.
- **컨테이너 한도(265MB) 직접 초과 경로(`oom_killed=true`)는 아직 테스트로 재현하지 못했다.**
- **비용 (2026-11-04 측정, 16코어·Docker VM 7.5GB 노트북, 테스트 10개 제출)**:
  | 항목 | Java | JavaScript |
  |---|---|---|
  | 제출 1건 전체 | 평균 5.3초 | 평균 1.0초 |
  | 그중 컴파일 등 테스트 외 시간 | 평균 3.9초 (대부분 `javac`, 최대 4.8초) | 0.3초 |
  | 테스트 10개 실행 합 | 약 1.0초 | 약 0.4초 |
  | Docker 호출 (생성/연결/시작/삭제) | 각 0.1~0.2초 | 같음 |

  Java 8개 동시 제출 시 1건 6.4초(+20%), 생성 0.2초·시작 0.6초까지 늘어남. `javac`가 컴파일 제한(10초)의 절반 가까이를 쓰므로, 배포 서버에서 다시 측정해 제한을 확인할 것.

### 비동기 채점 (제출·실행)

**용어**
- 요청 스레드: HTTP 요청을 받아 응답하는 일꾼. 이 일꾼이 채점까지 하면 응답이 5초씩 늦어진다.
- 작업 스레드: 응답과 상관없이 뒤에서 채점하는 일꾼. `JudgeExecutorConfiguration`의 실행기가 가상 스레드로 만든다.
- 폴링: 클라이언트가 조회 API를 일정 간격(1초쯤)으로 다시 불러 끝났는지 확인하는 방식.

**흐름** (제출 기준, 실행도 같음)
1. `POST /api/problems/1/submissions` → `SubmissionService#submit`이 트랜잭션 안에서 `PENDING` 행을 INSERT하고 `SubmissionCreatedEvent`를 남긴다.
2. 트랜잭션 커밋 → 요청 스레드는 바로 `202 {"id": 7, "status": "PENDING"}` 응답 (수십 ms).
3. 커밋 직후 `SubmissionJudgeWorker#onSubmissionCreated`가 작업 스레드에서 실행된다.
   - 트랜잭션 ①: `JUDGING`으로 표시
   - 트랜잭션 밖: 문제·테스트를 읽고 `Judge`로 채점 (Docker, 수 초)
   - 트랜잭션 ②: 테스트 결과 저장 + `DONE`·판정·점수 + AC면 `user_solved_problems` 기록 (하나라도 실패하면 셋 다 취소)
   - Docker 장애 같은 시스템 오류면 `FAILED`
4. 클라이언트가 `GET /api/submissions/7`을 반복 호출하다가 `DONE`이면 결과를 보여 준다.

**선택과 이유**
| 항목 | 선택 | 이유 (대안) |
|---|---|---|
| 비동기 방식 | 서버 안 작업 스레드 (`@Async` + 가상 스레드 실행기) | 서버 1대라 가장 단순하고 지연·불필요한 DB 조회가 없다. (DB 대기열: 1초마다 `PENDING`을 조회 + `FOR UPDATE SKIP LOCKED` — 서버 여러 대에 강하지만 복잡하고, 채점 중 꺼진 `JUDGING` 복구는 어차피 필요. 메시지 큐: 서버 여러 대가 되면) |
| 채점 시작 시점 | 커밋 **후** 이벤트 (`@TransactionalEventListener(AFTER_COMMIT)`) | 커밋 전에 넘기면 작업 스레드(다른 DB 연결)가 아직 커밋 안 된 행을 못 볼 수 있다. 저장이 롤백되면 채점도 시작되지 않는다 |
| 트랜잭션 범위 | `TransactionTemplate`으로 짧은 트랜잭션 두 개, 채점은 그 밖 | 수 초 걸리는 채점을 트랜잭션으로 묶으면 그동안 DB 연결을 붙잡아 연결 풀이 바닥난다 |
| 서버 재시작 | 시작 시 `PENDING`·`JUDGING`을 다시 채점 (`JudgeRecovery`) | "채점해 줘" 전달은 메모리에만 있어 서버가 꺼지면 사라진다. 결과는 끝날 때 한 트랜잭션으로만 저장되므로 처음부터 다시 채점하면 된다 |
| 결과 전달 | 조회 API 폴링 | Swagger로 확인하기 쉽다. STOMP 푸시는 맵·실시간 기능을 만들 때 같은 연결로 추가 |
| [코드 실행]도 비동기 | 예 | 동기면 7단계 대기열이 생긴 뒤 요청이 오래 기다리다 HTTP 시간 제한에 걸릴 수 있다 |
| 남의 제출·실행 조회 | 404 | 403이면 "그 번호의 제출이 있다"는 사실이 드러난다 |
| 푼 문제 기록 | `INSERT IGNORE` | "조회 → 없으면 저장"은 같은 유저의 AC 두 건이 동시에 끝나면 PK 충돌. DB가 한 문장으로 처리하면 경쟁이 없고 처음 시각도 보존된다 |
| 결과 화면 순서 | 결과 행 + 순서 목록(`id`, `hidden`) + 예제를 따로 읽어 메모리에서 합침 | `submission_test_results JOIN test_cases ORDER BY`는 1.7MB `input` 때문에 `Out of sort memory` (8장 "순서") |
| 시각 타입 | `Instant` | `LocalDateTime`은 시간대가 없어 Hibernate가 JVM 시간대(KST)로 보고 UTC로 한 번 더 바꿔 9시간 어긋났다 (실제로 겪음) |
| 비동기 테스트 | Awaitility (테스트 전용) | "DONE이 될 때까지 최대 120초, 300ms마다 확인"을 한 줄로 (`JudgeApiClient#awaitDone`). 직접 반복문을 쓰면 시간 제한 누락으로 테스트가 멈추기 쉽고, 실패 시 마지막 상태도 알려 준다 |
| 마이그레이션 | `V3__create_runs.sql` 새 파일 | 이미 적용된 V1을 고치면 Flyway가 체크섬 불일치로 서버 시작을 막는다. 바꿀 내용은 항상 새 번호 파일로 |

**주의한 Spring 동작**
- `@Async`·`@Transactional`은 Spring 프록시가 메서드 호출을 가로채서 동작한다. 같은 클래스 안에서 `this.method()`로 부르면 프록시를 거치지 않아 무시된다. 그래서 채점은 서비스와 다른 빈(`SubmissionJudgeWorker`)에 두고, 트랜잭션은 `TransactionTemplate`으로 직접 나눴다.
- 재시작 복구는 트랜잭션 밖에서 이벤트를 남긴다. 리스너의 `fallbackExecution = true`가 있어야 이런 이벤트도 처리된다.

**동시 제출 기준 측정** (`./gradlew concurrencyTest`, 대기열 없음, 2026-11-05, 16코어·Docker VM 8GB 노트북, 1회)
| 조건 | 전체 | 건별(접수→완료) 최소 / 중간 / 최대 |
|---|---|---|
| 제출 10건 동시 (Java 5, JS 5, 문제 1 정답 코드) | 9.0초 | 3.0초 / 7.0초 / 8.0초 |

모두 AC. 단독 제출(Java 약 5.3초, JS 약 1.0초, 7장 비용 표)보다 느려진 만큼이 동시 실행의 비용이다. `created_at`·`judged_at`이 초 단위 `DATETIME`이라 건별 시간은 1초 단위로 반올림된다. 7단계에서 대기열을 넣은 뒤 같은 테스트로 비교한다.

### 부하 대응 (나중에 적용)
| 시점 | 방법 |
|---|---|
| MVP (7단계) | 대기열 + 동시 컨테이너 수 제한(세마포어), 유저별 제출 빈도 제한 |
| (보류) | 첫 실패에서 중단: 프로그래머스식 전체 실행과 맞지 않아 제거함. 무한 루프 제출은 "시간 제한 × 테스트 수"만큼 걸린다 |
| 사용자 증가 | 컨테이너 미리 띄워두기(pre-warm, 1회용), JVM CDS/AOT 캐시, 같은 코드 결과 캐시 |
| 컴파일 단축 (측정 완료, 미적용) | `javac`에 `-J-XX:TieredStopAtLevel=1`(C1 JIT만 사용): 1코어에서 3.2초 → 1.5초. 래퍼를 이미지 빌드 시 미리 컴파일: 약 1초 추가 단축 예상. 유저 코드 실행에도 C1만 쓰는 안은 3초 이하 실행에서 같거나 더 빨랐음(1회 측정) → 반복 측정 후 결정 |
| 서버 1대 초과 | 채점 워커 분리 + 메시지 큐(Redis Streams/RabbitMQ) + 오토스케일 |
| 제출 내역 화면을 만들 때 | `submissions`에 (`user_id`, `created_at`) 인덱스(전체 내역 최신순), 요약 컬럼 `max_time_ms`·`max_memory_kb`(목록에서 테스트 결과 집계 생략, 기존 데이터는 보관된 테스트 결과로 채움) |

---

## 8. DB 설계 (MySQL, Flyway로 생성)

ERD: [`docs/erd.dbml`](docs/erd.dbml) (dbdiagram.io에 붙여 넣어 보기). 실제 테이블: `backend/src/main/resources/db/migration/V1__create_tables.sql`.
모든 테이블을 엔티티로 매핑했다. `runs`·`run_test_results`는 `V3__create_runs.sql`이 만든다. 아직 매핑하지 않은 것은 나중에 만들 `run_custom_test_results`뿐이다.

### 테이블
| 테이블 | 용도 | 키 |
|---|---|---|
| `users` | 로그인 회원 (`google_subject`로 식별, `nickname` 중복 금지) | `id` |
| `problems` | 제목, 난이도, 지문(마크다운), 반환 타입, 시간·메모리 제한 | `id` (seed 폴더 번호) |
| `problem_parameters` | `solution` 인자의 순서·이름·타입. 시작 코드 생성·지문 표시용 (채점은 유저 코드를 리플렉션으로 읽음) | (`problem_id`, `position`), (`problem_id`, `name`) UNIQUE |
| `test_cases` | 예제·히든 테스트 (입력·정답 JSON) | `id`, (`problem_id`, `sequence`) UNIQUE, (`problem_id`, `hidden`, `sequence`) 인덱스 |
| `submissions` | [제출] 기록: 코드, 언어, 상태(`PENDING`→`JUDGING`→`DONE`/`FAILED`), 판정, 점수, 컴파일 로그 | `id` |
| `submission_test_results` | 제출별 테스트 판정·시간·메모리. 예제만 반환값·출력·에러(앞 1KB) 저장 | (`submission_id`, `test_case_id`) |
| `user_solved_problems` | 푼 문제 (`solved_at` = 처음 AC 시각). `[완료]` 뱃지, Total Solved(`COUNT`) | (`user_id`, `problem_id`) |
| `runs` | [코드 실행] 기록: 코드, 언어, 상태, 판정, 컴파일 로그. `score` 없음 (예제 3~4개 통과 수라 결과 행에서 바로 셈) | `id` |
| `run_test_results` | 실행별 예제 결과. 예제는 공개라 반환값·출력·에러(앞 1KB) 항상 저장 | (`run_id`, `test_case_id`) |
| (나중) `run_custom_test_results` | 유저가 직접 추가한 테스트 입력의 결과. `input`·`expected`를 직접 가짐. `run_test_results`와 합치면 "`test_case_id`와 `input` 중 하나만" 같은 조건부 규칙이 생겨 분리 | (`run_id`, `position`) |

관계: `users` 1:N `submissions` 1:N `submission_test_results` N:1 `test_cases` N:1 `problems`, `problems` 1:N `problem_parameters`, `users` N:M `problems` (`user_solved_problems`), `users` 1:N `runs` 1:N `run_test_results` N:1 `test_cases`.

### 설계 판단
| 항목 | 결정 |
|---|---|
| 정규화 | 7개 테이블이 1~BCNF를 만족. `test_number` 제거(순서는 `test_cases.sequence`와 중복). `first_submission_id` 제거(유저·문제가 제출 테이블과 중복, 오래된 제출 삭제 시 FK 문제) |
| 파라미터 분리 | `parameters` JSON → `problem_parameters`. 1NF 때문이 아니라, 키가 있는 객체(`name`, `type`)라 키가 계속 붙을 수 있고 이름 중복·타입 제한을 DB가 검사하게 하려는 것. 문제당 1~3행이라 비용 거의 없음 |
| 의도한 역정규화 | `submissions.verdict`·`score` (채점 후 불변, CE는 테스트 결과가 없음), `user_solved_problems` 전체 (조회용) |
| JSON 유지 | `test_cases.input`·`expected`, `submission_test_results.actual`. 아래 기준을 모두 만족해서 하나의 값으로 취급 |
| 순서 | 표시·실행 순서는 `ORDER BY hidden, sequence` (예제가 항상 먼저, `Judge` 실행 순서와 일치). (`problem_id`, `hidden`, `sequence`) 인덱스로 정렬 없이 읽음: 1.7MB짜리 테스트 행을 정렬하면 MySQL 정렬 버퍼(기본 256KB)를 넘어 `Out of sort memory`로 실패했음. 같은 이유로 테스트 케이스는 `problems`와 조인해 읽지 않고(조인하면 인덱스 순서를 못 써서 다시 정렬함) `TestCaseRepository`로 따로 읽는다. `Problem` 엔티티에 테스트 케이스 컬렉션이 없는 이유 |
| 보관 | 전부 보관. 삭제 정책은 용량을 보고 추후 결정 |
| 공통 | 시간은 UTC `DATETIME`, 문자셋 `utf8mb4`, ON DELETE는 기본(RESTRICT) |

### JSON 컬럼 판단 기준
아래를 모두 만족할 때만 JSON 컬럼으로 두고, 그 외 값은 열로 둔다.
1. 안쪽 값으로 조회하거나 다른 테이블과 조인하지 않는다
2. 늘 통째로 읽고 통째로 쓴다 (테스트 입력은 그대로 래퍼 stdin으로, 반환값은 그대로 화면으로)
3. 키-값 객체가 아니라 값 나열형이다. 아무거나 끼워 넣으면 채점이 바로 깨져 테스트가 잡아낸다 (정크 서랍이 될 수 없음)
4. 원소 단위로 DB가 지켜야 할 규칙이 없다

나중에 JSON 안의 값으로 조회할 일이 생기면 JSON에서 꺼내지 말고 마이그레이션으로 열을 추가한다.
JPA 매핑은 `JsonNodeConverter`로 문자열 ↔ `JsonNode` 변환 (Hibernate 기본 JSON 매퍼는 추상 타입 `JsonNode`를 만들지 못함).

### DB가 검사할 규칙 (V1의 UNIQUE·CHECK 제약, `SchemaConstraintsTest`로 확인)
- `submissions`: `status = DONE`이면 `verdict`·`score`·`judged_at` NOT NULL, 그 외 상태면 셋 다 NULL
- `submissions`: `verdict = CE` ⇔ `compile_log` NOT NULL
- `runs`: `status = DONE`이면 `verdict`·`judged_at` NOT NULL, 그 외 상태면 둘 다 NULL. `verdict = CE` ⇔ `compile_log` NOT NULL (V3)
- 코드값 목록: `language`(JAVA, JAVASCRIPT), `status`, `verdict`(AC, WA, TLE, MLE, RE, CE), 파라미터 `type`·`return_type`(지원 타입 9개)
- 범위: `time_limit_ms > 0`, `memory_limit_mb >= 128`, `level` 0~5, `score` 0~100

### 애플리케이션 코드 + 테스트로 지킬 규칙 (다른 테이블·행을 봐야 해서 CHECK 불가)
- 제출의 테스트 결과는 같은 문제의 테스트 케이스만 가리킨다
- 히든 테스트 결과는 `actual`·`user_output`·`error_message`를 비운다
- 실행 결과는 같은 문제의 예제(`hidden = false`)만 가리킨다
- 테스트 입력의 인자 개수 = `problem_parameters` 행 수 (`ProblemRepositoryTest`)
- `problem_parameters.position`은 1부터 빈틈없이 이어진다 (`ProblemRepositoryTest`)
- `user_solved_problems`는 제출을 `DONE` + AC로 바꾸는 같은 트랜잭션에서 기록한다 (이미 있으면 무시)

---

## 9. 진행 상황

| 단계 | 내용 | 상태 |
|---|---|---|
| 0 | 모노레포 뼈대, Spring Boot 세팅, README | 완료 |
| 1 | 채점 이미지(Java/JS) + 백엔드 `SandboxRunner` + 통합 테스트 | 완료 |
| 2-1 | 전체 테스트 실행 + 문제별 시간·메모리 제한(기본값) | 완료 |
| 2-2 | 프로그래머스식 함수형: 언어별 래퍼 코드 + JSON 인자/반환값 (지원 타입: int, long, double, boolean, String, int[], long[], String[], int[][]) | 완료 |
| 2-3 | seed 문제 5개 (짝수 개수, 반복 글자 줄여 쓰기, 테두리 합, 평균 이상 비율, 최대 연속 구간 합) + 검증용 정답·오답·느린 코드 | 완료 |
| 2-4 | 판정기(AC/WA/TLE/MLE/RE/CE) + 점수, 문제별 검증용 코드로 판정 테스트 | 완료 |
| 3 | DB 구조 설계 (ERD, 8장) + 기술 선택 (JPA, Docker Compose, Testcontainers, Flyway) | 완료 |
| 4 | MySQL + Flyway(V1 테이블·제약, V2 seed 문제), 문제 엔티티·Repository, `Judge`가 DB 문제로 채점 | 완료 |
| 5 | API: 문제 목록·상세(시작 코드 포함), 코드 실행 + Swagger UI | 완료 |
| 6 | Google OAuth + JWT, 제출 API(`submissions` 저장·조회) + 실행 기록 저장(`runs`) + 비동기 채점 + 결과 전달(조회 API 또는 STOMP 푸시), `user_solved_problems`/Total Solved, 동시 제출 JUnit 테스트(대기열 없는 상태의 기준 측정) | 완료 (결과 전달은 폴링, STOMP는 실시간 기능 때) |
| 7 | 대기열(동시 채점 수 제한) + Docker 호출 시간 제한, 동시 제출 테스트로 6단계 측정값과 비교 (배포 전 필수) | 예정 |
| 8 | 부하 테스트 도구(k6 등, 사전 검토 후) 도입: 실제 서버에 가상 유저로 응답 시간 분포 측정 (배포 전) | 예정 |
