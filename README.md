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
| 로그인 방식 | Google 로그인 성공 → `users`를 Google 계정 ID(`sub`)로 찾거나 생성 → 우리 서버 JWT 발급. 토큰은 **HttpOnly 쿠키**로 심고(4장 "인증"), Swagger·테스트용으로 `Authorization: Bearer` 헤더도 받는다. 처음에는 헤더만 쓰기로 했다가 프론트 연결 때 쿠키로 바꿈(자바스크립트가 토큰을 읽지 못해 XSS로 탈취 불가, WebSocket 연결에도 자동으로 붙음). HS256 서명(서버 하나라 비밀키 하나로 충분). 액세스 토큰 24시간, 리프레시 토큰 없음(만료 시 재로그인, 보안 강화 때 도입). 로그인 과정의 세션은 Google state 검증에만 쓰고 바로 폐기 |
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
| 페어 요청 | — | — | 로그인한 관전자만, 회원 주인의 방에서만 |
| 전체 채팅 | 누구나 (게스트 포함) | | |

코드 수정·페어 채팅·페어 요청 권한은 화면이 아니라 서버(`DeskRooms`)가 강제한다 (10장 "페어").

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
| FR-02-5 | 라운지 전체 채팅 | 게스트 포함 누구나. 보낸 고양이 머리 위 말풍선 4초 + 라운지 채팅 창. 200자, 1초에 1개, 최근 30개만 서버 메모리 (2026-11-12 MVP에 추가) |
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
- 스터디룸(링크 전용 맵), 근접 채팅(가까운 사람끼리만)·음성·화상, 라이브 코딩(면접관/지원자) 모드, 손들기, 채널 분할
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
| 실시간 | STOMP (Spring simple broker) | (2단계 예정) 아바타 위치·상태·이펙트·채점 결과 알림 브로드캐스트. 연결할 때 HttpOnly 쿠키로 신원 확인, 메시지는 연결에 붙은 신원만 믿음, 게스트는 연결마다 서버가 id 발급, 위치는 최대 100ms 간격 (10장) |
| 동시 편집·관전 | **Yjs** 13.6 + `y-monaco` 0.1 + `y-protocols`(awareness) | 병합은 브라우저의 CRDT가 하고 서버(Spring)는 방 단위로 바이트만 중계 (10장 "관전"). 대안 비교: Loro·Automerge는 Monaco 연결이 0.0.x 실험 단계, ShareDB(OT)는 Node 서버 필수, 호스팅 서비스는 유료·외부 의존 |
| 인증 | **Spring Security 7** OAuth2 Client(Google 로그인) + OAuth2 Resource Server(JWT 검증, 내장 Nimbus) | 별도 JWT 라이브러리(jjwt) 없이 발급·검증. 토큰은 **HttpOnly 쿠키**(`SameSite=Lax`)로 심어 프론트 자바스크립트가 읽지 못하게 함(XSS로 토큰 탈취 불가). 서버는 `Authorization` 헤더를 먼저, 없으면 쿠키를 읽음(`BearerTokenResolver`). CSRF는 `SameSite=Lax`가 다른 사이트의 POST에 쿠키를 붙이지 않는 것으로 막음 |
| 채점 | **Docker + docker-java 3.7.1** | 제출마다 격리 컨테이너 생성. CLI 호출보다 종료 코드·OOM 등을 구조화된 값으로 받을 수 있다 |
| API 문서 | **springdoc-openapi 3.2.0** (Swagger UI) | 컨트롤러에서 API 문서가 자동으로 만들어지고 브라우저에서 바로 요청을 보내 볼 수 있음. Spring Boot 4.1.2 기준 빌드 |
| 테스트 | JUnit 5, AssertJ, **Testcontainers 2** | 샌드박스와 DB는 실제 Docker 컨테이너를 띄우는 통합 테스트(`judgeTest`) |
| 기타 | Lombok(JPA 엔티티 한정), DTO는 `record` | |
| 라운지 지도 | **Phaser 4.2.1** (2D 게임 프레임워크) | 게임 루프·스프라이트 애니메이션·충돌·키 입력·카메라가 내장. 3.x는 2025-05의 3.90이 마지막이라 현재 안정판인 4를 고름. 3D·PixiJS 비교는 `docs/ui-design.md` 5장, React 연결 방식은 10장 |
| 프론트 기반 | **Vite 8 + React 19 + TypeScript 7**, pnpm, vanilla-extract(빌드 때 CSS를 만드는 TypeScript 스타일) + 타입 있는 테마 토큰, oxlint + Prettier, FSD | CRA는 지원 종료, Next.js의 SSR은 Phaser·Monaco처럼 브라우저 전용 앱에 이득이 없음. 스타일은 vanilla-extract(아래 "스타일" 줄). 코드 규칙은 `.claude/skills/react-best-practices`(Vercel, Next.js 전용 `server-` 규칙 제외). barrel 파일(`index.ts` 재수출)은 두지 않고 FSD 슬라이스도 파일을 직접 경로로 import |
| 스타일 | **vanilla-extract** 1.21 (`@vanilla-extract/css`, `recipes`, Vite 플러그인) | 처음엔 CSS Modules + CSS 변수였다(2026-11-11 전환). 토큰 이름을 문자열로 쓰면 오타가 나도 조용히 무시된다 — 실제로 `--map-pot`, `--map-star`가 빠져 있었는데 오류가 없었다. 테마를 `createGlobalThemeContract`로 만들어 `vars.color.paper`처럼 쓰면 틀린 이름은 빌드가 실패한다. 변수 이름은 계약에 직접 지정해 `--color-paper` 그대로 유지(Phaser가 같은 변수를 `tokenColor(vars.map.pot)`로 읽음). 버튼 변형은 `recipe`라 `variant`/`size` 타입이 스타일 정의에서 나온다. 실행 중 비용 없이 빌드 때 CSS 파일로 뽑는다. Tailwind는 사용자 선호로 제외, Panda·StyleX는 원자 클래스 방식이라 기존 "이름 붙인 스타일" 구조와 거리가 멀어 제외. 테마는 `shared/styles/theme.css.ts`, 전역 스타일은 `app/styles/global.css.ts` |
| 풀이 화면 | **@monaco-editor/react + monaco-editor**(CDN 대신 설치한 패키지, `React.lazy`로 늦게 로드), react-markdown + remark-gfm, react-resizable-panels, **TanStack Query**, React Router | 실행·제출은 접수(202) 후 결과를 1초마다 폴링 → TanStack Query `refetchInterval`이 `DONE`·`FAILED`에서 멈춤. 코드는 React 상태에 두지 않고 실행·제출 순간에만 에디터에서 읽어 타이핑 중 재렌더가 없음. 작성 중 코드는 문제·언어별로 localStorage |

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
├─ frontend/                     React + Vite (dev 서버 3000, /api → 8080 프록시)
│  ├─ public/assets/             CC0 에셋 (출처·라이선스는 CREDITS.md)
│  └─ src/                       FSD: app(진입점·공통 레이아웃·전역 스타일·테마 토큰) / pages / widgets(lounge-map 등) / features / entities(avatar, lounge 등) / shared
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
./gradlew concurrencyTest   # 제출 10건 동시 채점 측정 (무거워서 judgeTest와 분리). -PjudgeConcurrency=2 로 동시 채점 수를 바꿔 비교
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

로그인: 프론트(http://localhost:3000)의 [로그인] 또는 http://localhost:8080/oauth2/authorization/google → Google 로그인 → 백엔드가 `accessToken` HttpOnly 쿠키를 심고 `http://localhost:3000/login/callback`으로 보냄 → 로그인 전 보던 문제로 돌아감. 쿠키는 포트를 가리지 않아 같은 브라우저의 Swagger(8080)에서도 그대로 인증된다. 쿠키 없이 테스트할 때는 Swagger **Authorize**에 토큰을 넣는다(헤더가 쿠키보다 먼저).

설정: `FRONTEND_URL`(기본 `http://localhost:3000`), `COOKIE_SECURE`(기본 `false`, 배포 HTTPS에서는 `true`).

서버를 켠 뒤 http://localhost:8080/swagger-ui.html 에서 API를 확인한다.

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/api/problems` | 문제 목록 (`id`, `title`, `level`, `solved`). 로그인했으면 `solved`에 내가 푼 문제인지([완료] 뱃지), 게스트는 모두 `false` |
| GET | `/api/problems/{id}` | 문제 상세 (지문, 파라미터, 반환 타입, 시간·메모리 제한, 언어별 `starterCode`) |
| POST | `/api/problems/{id}/runs` | [코드 실행] `{"language": "JAVA", "sourceCode": "..."}` → 예제만 채점. 접수만 하고 `202 {"id", "status": "PENDING"}`. **로그인 필요** |
| GET | `/api/runs/{id}` | 실행 결과 (상태, 판정, 통과 수, 예제별 입력·기댓값·반환값·출력). `status`가 `DONE`이 될 때까지 다시 부른다. 본인 것만 |
| POST | `/api/problems/{id}/submissions` | [제출] 예제 + 히든 전부 채점. 접수만 하고 `202`. **로그인 필요** |
| GET | `/api/submissions/{id}` | 제출 결과 (상태, 판정, 점수, 테스트별 결과. 히든은 판정·시간·메모리만). 본인 것만 (남의 것은 404) |
| GET | `/api/problems/{id}/submissions?page=0&size=20` | 이 문제에 낸 내 제출 내역, 최신순 (`id`, `language`, `status`, `verdict`, `score`, `createdAt`, `judgedAt`). 소스 코드·테스트별 결과는 빠짐. `hasNext`가 `true`면 `page+1`. `size`는 1~50. **로그인 필요** |
| GET | `/api/users/me` | 내 정보 (`id`, `nickname`, `email`, `totalSolved`). **로그인 필요** |
| GET | `/oauth2/authorization/google` | Google 로그인 시작 → 성공하면 `accessToken` HttpOnly 쿠키를 심고 프론트 `/login/callback`으로 리다이렉트 |
| POST | `/api/auth/logout` | 로그아웃. 만료된 쿠키로 덮어써 지운다(HttpOnly라 프론트가 직접 못 지움). `204` |

에러는 표준 형식(RFC 9457 `ProblemDetail`): 없는 문제 404, 빈 코드·64K자 초과·지원하지 않는 언어 400. 로그인이 필요한 API에 토큰이 없거나 틀리면 401 (Google 로그인 화면으로 보내지 않음).
채점이 밀리면 429: 내 끝나지 않은 제출·실행이 이미 2건이거나, 전체 대기가 50건일 때. 동시에 채점하는 수는 4건으로 제한하고 나머지는 `PENDING`으로 차례를 기다린다. 비동기 채점의 동작은 7장 "비동기 채점", 제한의 동작과 이유는 7장 "부하 제한".

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

### 부하 제한 (7단계: 동시 채점 수 · 접수 제한 · Docker 호출 시간 제한)

임시값과 다시 정할 계획은 [`docs/tuning.md`](docs/tuning.md).

#### 왜 필요한가
6단계까지는 제출이 들어오는 대로 컨테이너를 바로 띄웠다. 100건이 몰리면 컨테이너 100개(최대 265MB씩, 약 26GB)가 한꺼번에 떠서 메모리가 먼저 바닥나고, 그 전에 CPU 경쟁으로 모든 채점이 함께 느려진다. 그래서 세 겹으로 막는다.

| 겹 | 막는 것 | 코드 | 넘으면 |
|---|---|---|---|
| ① 접수 제한 | 한 유저의 연타, 끝없이 길어지는 대기 | `JudgeAdmission` | 429, 저장하지 않음 |
| ② 동시 채점 수 제한 | 한꺼번에 뜨는 컨테이너 수 | `JudgeSlots` (세마포어) | `PENDING`인 채로 차례를 기다림 |
| ③ Docker 호출 시간 제한 | 멈춘 Docker 때문에 채점 자리가 영영 안 돌아오는 것 | `DockerClientConfiguration`, `SandboxRunner` | 예외 → `FAILED`, 자리 반납 |

#### 실제 흐름 (동시 채점 수 N=2, 유저 셋이 거의 동시에 제출)
1. A·B·C가 제출 → 각각 ① 접수 제한 통과 → `PENDING` 저장 → 202 응답. 여기까지는 6단계와 같다.
2. 커밋 후 작업 스레드(가상 스레드) 3개가 생겨 `JudgeSlots#runInSlot`에서 허가증을 요청한다.
3. A·B는 허가증을 받고 `JUDGING`으로 바꾼 뒤 컨테이너를 띄운다. C는 허가증이 없어 **`PENDING`인 채로 그 자리에서 기다린다**(스레드가 멈춰 있지만 가상 스레드라 OS 스레드는 놓아준다).
4. A가 끝나면 `finally`에서 허가증을 돌려준다 → 기다리던 C가 받아 `JUDGING`이 된다.
5. 화면은 폴링으로 C의 상태를 보므로 "대기 중(PENDING) → 채점 중(JUDGING) → 완료(DONE)"를 구분해 보여 줄 수 있다.
6. 그 사이 A가 또 제출하고 실행까지 눌러 A의 끝나지 않은 건이 2개가 되면, 세 번째 요청은 ①에서 `429 "채점이 끝나지 않은 제출·실행이 이미 2건 있습니다"`.

#### 공부할 개념

**1. 세마포어 (`java.util.concurrent.Semaphore`)**
- 허가증 N장을 가진 관리인. `acquire()` = 한 장 받기(없으면 생길 때까지 기다림), `release()` = 한 장 돌려주기.
- 화장실 열쇠 비유: 열쇠가 2개면 동시에 2명만 들어가고, 나머지는 줄을 선다. 나온 사람이 열쇠를 돌려줘야 다음 사람이 들어간다.
- 락(`synchronized`, `ReentrantLock`)은 "한 명만"이고 세마포어는 "N명까지"다. N=1인 세마포어는 락과 비슷하지만, 락과 달리 받은 스레드가 아닌 다른 스레드도 돌려줄 수 있다(소유자 개념이 없다).
- **`release`는 반드시 `finally`에서**: 채점 중 예외가 났는데 돌려주지 않으면 허가증이 한 장 사라진다(누수). N번 쌓이면 0장이 되어 이후 모든 채점이 영원히 기다린다. `JudgeSlotsTest`의 두 번째 테스트가 이것을 확인한다.
- **`acquire`는 `try` 밖에서**: 기다리다 인터럽트되면 허가증을 받지 못한 것이므로 돌려주면 안 된다. `try` 안에 넣으면 받지도 않은 허가증을 돌려줘 N+1장이 된다.

**2. 공정(fair) 모드**
- `new Semaphore(N, true)`. 기다린 순서대로 허가증을 준다(FIFO 대기열).
- 기본값(`false`, 비공정)은 허가증이 반납되는 순간 막 도착한 스레드가 줄 선 스레드보다 먼저 가져갈 수 있다(새치기). 처리량은 조금 더 좋지만 운 나쁜 제출이 계속 밀릴 수 있다(기아, starvation).
- 채점은 한 건이 수 초라 공정 모드의 추가 비용(대기열 관리)은 무시할 만하고, "먼저 낸 사람이 먼저 결과를 받는다"가 더 중요해서 공정 모드를 골랐다.

**3. 가상 스레드는 "풀"이 아니라 "세마포어"로 제한한다**
- 일반(플랫폼) 스레드는 OS 스레드 1개를 통째로 쓰고 만들기 비싸서, 미리 N개 만들어 두고 돌려 쓴다(스레드 풀). 풀 크기가 곧 동시 실행 수 제한이었다.
- 가상 스레드는 JVM이 관리하는 가벼운 스레드라 작업마다 새로 만들고 버리는 것이 원래 쓰는 법이다. 기다리는(블로킹) 동안에는 OS 스레드를 놓아줘서 수천 개가 기다려도 부담이 적다.
- 그래서 JDK 문서([JEP 444](https://openjdk.org/jeps/444) "Do not pool virtual threads")는 가상 스레드를 풀에 담지 말고, 제한이 필요한 자원(여기서는 Docker 컨테이너) 앞에 세마포어를 두라고 한다. 제한하고 싶은 건 "스레드 수"가 아니라 "동시에 쓰는 자원 수"이기 때문이다.

**4. 접수 제한(admission control)과 429**
- 세마포어는 "동시에 채점하는 수"만 제한하고 "기다리는 수"는 제한하지 않는다. 대기가 1000건 쌓이면 맨 뒤는 수십 분을 기다린다. 오래 기다리게 하느니 접수할 때 "지금은 붐빈다"를 바로 알려 주는 편이 낫다(빠른 실패, fail fast).
- 429 Too Many Requests: "요청이 너무 많다"는 HTTP 표준 상태 코드. 503(서버가 일을 못 함)과 달리 "잠시 뒤 다시 하면 된다"는 뜻이다.
- **대기열 상한 계산**: 허용 대기 시간 ÷ 1건 채점 시간 × 동시 채점 수. 60초 ÷ 5.3초(Java) × 4 ≈ 45 → 50건. 리틀의 법칙(Little's Law: 대기 수 = 도착률 × 대기 시간)을 거꾸로 쓴 것과 같은 생각이다.
- **유저별 진행 중 개수 제한** vs **빈도 제한(rate limit)**: 빈도 제한은 "1분에 10번"처럼 시간 창을 센다(토큰 버킷, 슬라이딩 윈도우 같은 알고리즘이 필요). 여기서 막고 싶은 건 "한 사람이 대기열을 독차지하는 것"이라, 그 유저의 끝나지 않은 건수(K=2)를 세는 쪽이 목적에 바로 맞고 DB 개수 조회 한 번이면 된다. 결과를 보고 고쳐서 다시 내는 정상 사용자는 막히지 않는다.

**5. check-then-act 경쟁 (알고 남겨 둔 한계)**
- `JudgeAdmission`은 "개수 세기(check) → 저장(act)"을 두 단계로 한다. 같은 유저의 요청 두 개가 거의 동시에 오면 둘 다 "지금 1건"을 읽고 둘 다 저장해 3건이 될 수 있다.
- 6단계의 푼 문제 기록은 `INSERT IGNORE` 한 문장으로 이 문제를 없앴다. 여기서는 개수 조건이라 한 문장으로 만들기 어렵다. 정확히 막으려면 유저 행을 `SELECT ... FOR UPDATE`로 잠가 같은 유저의 접수를 한 줄로 세우면 된다.
- 상한을 한두 건 넘는 것은 서버를 지키는 데 지장이 없어(실제 동시 채점은 세마포어가 정확히 막는다) 지금은 허용했다.

**6. 시간 제한(timeout)이 왜 세마포어의 짝인가**
- `SandboxRunner`는 Docker 엔진에 HTTP로 명령한다: 생성 → 연결(attach) → 시작 → 끝날 때까지 대기 → 삭제. 6단계에는 "끝날 때까지 대기"(`awaitCompletion`)에만 시간 제한이 있었다.
- 엔진이 멈추면(재시작 중, 디스크 가득 참) 나머지 호출이 응답 없이 영원히 기다릴 수 있다. 세마포어를 넣은 뒤에는 더 위험하다: 멈춘 채점이 허가증을 쥔 채로 남고, N개가 다 멈추면 서버 전체 채점이 멈춘다.
- 걸어 둔 시간 제한:
  | 시간 제한 | 값 | 뜻 |
  |---|---|---|
  | 연결 시간 제한 (`connectionTimeout`) | 3초 | Docker 엔진과 연결이 맺어질 때까지. 엔진이 꺼져 있으면 3초 만에 실패 |
  | 응답 시간 제한 (`responseTimeout`) | 2분 | 요청 후 응답 데이터가 올 때까지. **attach 스트림은 컨테이너가 끝날 때까지 아무것도 보내지 않으므로**, 가장 긴 컨테이너 시간(컴파일 10초 + 3초 × 테스트 11개 + 여유 15초 = 58초)보다 길어야 한다. 짧게 잡으면 정상 채점이 끊긴다 |
  | attach 시작 대기 (`ATTACH_TIMEOUT`) | 10초 | attach 연결이 열릴 때까지. 평소 0.1~0.2초 |
  | 컨테이너 대기 (`awaitCompletion`, 6단계부터) | 컴파일 10초 + 시간 제한 × 테스트 수 + 15초 | 컨테이너가 끝날 때까지 |
- 시간 제한에 걸리면 예외 → 작업자가 `FAILED`로 표시 → `runInSlot`의 `finally`가 허가증을 돌려준다. 컨테이너는 `SandboxRunner`의 `finally`가 강제 삭제한다.

#### 선택과 이유
| 항목 | 선택 | 버린 대안과 이유 |
|---|---|---|
| 동시 채점 수 제한 | 작업자 안에서 세마포어(`JudgeSlots`), 제출·실행이 함께 씀 | **`SimpleAsyncTaskExecutor#setConcurrencyLimit`**: 한도에 걸리면 작업을 *넘기는* 스레드가 멈춘다. 지금은 그게 AFTER_COMMIT 이벤트를 처리하는 요청 스레드라 202 응답이 채점 끝날 때까지 늦어진다. **고정 크기 스레드 풀(`ThreadPoolTaskExecutor`)**: 동작은 하지만 가상 스레드를 버리고 플랫폼 스레드 N개 + 대기열을 따로 관리해야 한다. **DB 대기열(`FOR UPDATE SKIP LOCKED` 폴링)**: 서버가 여러 대일 때 좋지만 1초 폴링 지연과 쿼리 부하가 생긴다. 기다리던 작업이 서버 종료로 사라져도 DB에 `PENDING`으로 남고 `JudgeRecovery`가 다시 채점하므로, 서버 1대에서는 DB 대기열의 장점(유실 없음)을 이미 갖고 있다. **메시지 브로커(Redis/RabbitMQ/Kafka)**: 서버를 하나 더 운영해야 해서 과함 |
| 세마포어 위치 | 자리를 받은 *뒤에* `JUDGING`으로 바꿈 | 먼저 `JUDGING`으로 바꾸면 기다리는 건도 "채점 중"으로 보여 화면이 거짓말을 한다 |
| 접수 제한 위치 | 저장 전, 같은 트랜잭션 (`SubmissionService#submit`, `RunService#run`) | 작업자에서 거절하면 이미 202를 준 뒤라 "접수됐는데 거절됨" 상태가 생긴다 |
| 대기열·유저 개수 세는 곳 | DB (`PENDING`·`JUDGING` 개수, status·user 인덱스) | 메모리 카운터는 빠르지만 서버 재시작·복구 때 DB와 어긋난다. 접수는 수 초에 한 번이라 DB 조회 비용이 작다 |
| 유저별 제한 | 끝나지 않은 건 K=2 | 빈도 제한(rate limit)은 목적(독차지 방지)에 간접적이고 알고리즘이 더 필요 |
| 기다리다 서버 종료 | 인터럽트되면 로그만 남기고 `PENDING` 유지 | 다음 시작 때 `JudgeRecovery`가 다시 채점 |

#### 동시 제출 측정 (`./gradlew concurrencyTest -PjudgeConcurrency=N`, 2026-11-11, 16코어·Docker VM 7.5GB 노트북, 각 1회)
제출 10건(Java 5, JS 5)을 동시에 보냈을 때. 모두 AC.
| 동시 채점 수 N | 전체 | 건별(접수→완료) 최소 / 중간 / 최대 |
|---|---|---|
| 제한 없음 (6단계 기준값) | 9.0초 | 3.0 / 7.0 / 8.0초 |
| 2 | 20.9초 | 2.0 / 14.0 / 20.0초 |
| **4 (지금 값)** | 12.4초 | 2.0 / 9.0 / 12.0초 |
| 8 | 23.6초 | 14.0 / 18.0 / 23.0초 |
| 10 (= 제출 수, 사실상 제한 없음) | 10.1초 | 5.0 / 9.0 / 10.0초 |

- **읽는 법**: N=2 → 4에서 전체 시간이 절반 가까이 줄었다. 자리가 적으면 줄이 길어진다는 것이 그대로 보인다. N=4는 제한 없음보다 3초쯤 느리지만, 컨테이너가 동시에 4개(약 1GB)까지만 뜬다는 보장을 얻는다. 노트북 16코어에서는 10건 정도는 한꺼번에 돌려도 버티므로 제한의 이득(메모리 보호)이 시간에 드러나지 않는다. 이득은 제출이 수십 건 몰릴 때 나타난다.
- **N=8이 23.6초로 튄 것은 측정 잡음으로 본다**: N=10(10.1초)보다 느릴 이유가 없고, 각 1회 측정이라 노트북의 다른 작업·절전·Docker VM 상태에 크게 흔들린다. 건별 *최소*가 14초인 것은 첫 채점부터 느렸다는 뜻이라 대기열 문제가 아니다.
- 그래서 이 표로 "최적 N"을 정하지 않는다. 배포 서버에서 N별로 여러 번(3회 이상) 재서 중간값으로 정한다 (`docs/tuning.md`).

#### 검증
- `JudgeSlotsTest` (Docker 불필요): 작업 8개를 넣어도 동시에 2개까지만 돈다 / 채점 중 예외가 나도 허가증을 돌려준다.
- `JudgeApiTest#limitsUnfinishedJudgingPerUser`: 끝나지 않은 건 2개면 세 번째는 429, 끝나면 다시 받는다.
- `JudgeApiTest#rejectsWhenQueueIsFull`: 전체 `PENDING`이 50건이면 처음 제출하는 유저도 429.
- `DockerClientTimeoutTest` (Docker 불필요): **멈춘 Docker 엔진 흉내** — 테스트 안에서 `ServerSocket`을 열어 연결은 받아 주되 응답을 한 바이트도 보내지 않는 가짜 서버를 만들고, Docker 클라이언트를 그 주소(`tcp://127.0.0.1:<포트>`)로 연결한다. 응답 시간 제한 1초를 걸면 `createContainerCmd`가 약 1초 뒤 예외로 끝나는지 본다. 시간 제한이 동작하지 않으면 테스트가 멈추므로 테스트 자체에도 10초 상한(`assertTimeoutPreemptively`)을 걸었다. 응답 시간 제한을 1시간으로 바꾸면 이 상한에 걸려 실패하는 것도 확인했다(테스트가 정말 시간 제한을 잡아내는지 거꾸로 검증).
  - 이를 위해 `DockerClientConfiguration#create`가 접속 주소를 인자로 받게 나눴다. 원래는 환경 변수(`DOCKER_HOST`)에서만 읽어 테스트가 가짜 주소를 넣을 수 없었다.
  - 연결 시간 제한(`connectionTimeout`)은 테스트하지 않았다. "연결을 받지도 거절하지도 않는 주소"를 만들어야 하는데, 존재하지 않는 IP로 보내면 네트워크 환경에 따라 바로 거절되기도 해서 결과가 PC마다 달라진다.
  - `ATTACH_TIMEOUT`도 따로 테스트하지 않았다. 가짜 서버에서는 attach 요청이 응답 시간 제한에 먼저 걸린다.

### 제출 내역 (`GET /api/problems/{id}/submissions`)

**흐름**: 프론트가 문제 화면의 [제출 내역] 탭을 열면 `?page=0&size=20` 호출 → 최신 20건과 `hasNext` 응답 → `hasNext`가 `true`면 [더 보기]로 `page=1` 호출 → 한 줄을 누르면 기존 `GET /api/submissions/{id}`로 코드·테스트별 결과를 본다.

#### 공부할 개념

**1. InnoDB 보조 인덱스에는 기본키가 숨어 있다**
- InnoDB 테이블은 기본키(`id`) 순서로 행 자체를 저장한다(클러스터드 인덱스). 그 밖의 인덱스(보조 인덱스)는 "인덱스 컬럼 값 + 그 행의 기본키"를 저장하고, 행이 필요하면 그 기본키로 다시 찾아간다.
- 그래서 `ix_submissions_user_problem (user_id, problem_id)`는 실제로 `(user_id, problem_id, id)` 순서로 정렬돼 있다.
- "이 유저·이 문제를 `id` 내림차순으로 20개"는 인덱스에서 그 구간을 찾아 **뒤에서부터 20개 읽고 멈추면** 끝난다. 정렬(filesort)이 필요 없다. 새 인덱스를 만들지 않은 이유다.
- `created_at`이 아니라 `id`로 정렬하는 것도 이 때문이다. `id`는 `AUTO_INCREMENT`라 늦게 낸 제출일수록 크므로 순서가 같다. `created_at`으로 정렬하면 (`user_id`, `problem_id`, `created_at`) 인덱스를 따로 만들어야 한다.
- 확인 방법: `EXPLAIN SELECT ... WHERE user_id = 1 AND problem_id = 1 ORDER BY id DESC LIMIT 21`의 `key`가 `ix_submissions_user_problem`이고, `Extra`에 `Using filesort`가 없다 (`SubmissionHistoryTest#usesIndexOrderWithoutSorting`).

**2. Page vs Slice (페이지 나누기)**
| | Page | Slice (선택) |
|---|---|---|
| 아는 것 | 전체 개수, 전체 페이지 수 | 다음 페이지가 있는지만 |
| 쿼리 | 목록 + `COUNT(*)` 2개 | 목록 1개 (요청한 개수보다 1개 더 읽어 다음이 있는지 판단) |
| 맞는 화면 | 1, 2, 3 ... 페이지 번호 | [더 보기], 무한 스크롤 |
- 제출 내역은 "최근 것부터 보다가 필요하면 더 보기"라 전체 개수가 필요 없다. 그래서 `COUNT` 쿼리를 아끼는 `Slice`를 골랐다.
- `Slice`를 그대로 JSON으로 내보내지 않고 `SubmissionHistoryResponse(submissions, hasNext)`로 옮긴다. 그대로 내보내면 `pageable`·`sort` 같은 내부 구조가 응답에 섞이고 Spring Data 버전에 따라 모양이 바뀐다.
- 한계: `page` 번호 방식(OFFSET)은 뒤 페이지로 갈수록 앞의 행을 읽고 버리는 비용이 늘어난다. 한 문제에 수천 건을 내는 일은 드물어 지금은 괜찮다. 커지면 "마지막으로 본 `id`보다 작은 것"으로 찾는 커서 방식(`WHERE id < :lastId`)으로 바꾼다.

**3. 필요한 컬럼만 읽기 (JPQL 생성자 표현식)**
- `select new com.codingtest.submission.SubmissionSummary(submission.id, ...)`처럼 쓰면 엔티티 대신 필요한 컬럼만 읽어 record로 바로 만든다(DTO 프로젝션).
- 엔티티(`Submission`)로 읽으면 행마다 `source_code`(MEDIUMTEXT, 최대 64K자)까지 가져온다. 20건이면 최대 1MB를 읽고 버리는 셈이다.

**4. 파라미터 검증 (`@Min`, `@Max`)**
- 컨트롤러 파라미터에 `@Min(1) @Max(50)`을 바로 붙이면 Spring MVC 6.1+가 메서드 검증을 해서 범위를 벗어나면 400을 돌려준다.
- `size` 상한은 `size=100000` 같은 요청으로 한 번에 너무 많은 행을 읽지 않게 하려는 것이다.

#### 선택과 이유
| 항목 | 선택 | 이유 (대안) |
|---|---|---|
| 범위 | 문제별 내 제출만 | 프로그래머스의 "제출 내역" 탭과 같다. 내 전체 제출 목록은 (`user_id`, `created_at`) 인덱스가 따로 필요해 화면이 생길 때 추가 |
| 실행(`runs`) 내역 | 넣지 않음 | 실행은 예제만 돌려 보는 연습이라 기록으로 보여 줄 필요가 적다 (프로그래머스도 제출만 보여 줌) |
| 없는 문제 | 404 | 제출 API와 같은 규칙. 빈 목록이면 "문제 번호가 틀렸다"를 알 수 없다 |
| 응답 항목 | 판정·점수·언어·시각만 | 목록에서는 코드를 보지 않는다. 자세한 내용은 기존 단건 조회로 |

#### 검증 (`SubmissionHistoryTest`)
- 내 제출만 최신순, 다른 문제·남의 제출은 섞이지 않음, `size=2`로 나눴을 때 `hasNext`, 응답에 `sourceCode`가 없음
- 없는 문제 404, `size` 0·51은 400, 토큰 없으면 401
- `EXPLAIN`으로 인덱스 사용·정렬 없음 확인 (위 개념 1)

### 부하 대응 (나중에 적용)
| 시점 | 방법 |
|---|---|
| MVP (7단계, 적용함) | 동시 채점 수 제한(세마포어) + 접수 제한(대기열 상한, 유저별 진행 중 개수) + Docker 호출 시간 제한. 위 "부하 제한" |
| (보류) | 첫 실패에서 중단: 프로그래머스식 전체 실행과 맞지 않아 제거함. 무한 루프 제출은 "시간 제한 × 테스트 수"만큼 걸린다 |
| 사용자 증가 | 컨테이너 미리 띄워두기(pre-warm, 1회용), JVM CDS/AOT 캐시, 같은 코드 결과 캐시 |
| 컴파일 단축 (측정 완료, 미적용) | `javac`에 `-J-XX:TieredStopAtLevel=1`(C1 JIT만 사용): 1코어에서 3.2초 → 1.5초. 래퍼를 이미지 빌드 시 미리 컴파일: 약 1초 추가 단축 예상. 유저 코드 실행에도 C1만 쓰는 안은 3초 이하 실행에서 같거나 더 빨랐음(1회 측정) → 반복 측정 후 결정 |
| 서버 1대 초과 | 채점 워커 분리 + 메시지 큐(Redis Streams/RabbitMQ) + 오토스케일 |
| 내 전체 제출 목록을 만들 때 (문제별 내역은 만듦, 위 "제출 내역") | `submissions`에 (`user_id`, `created_at`) 인덱스(전체 내역 최신순), 요약 컬럼 `max_time_ms`·`max_memory_kb`(목록에서 테스트 결과 집계 생략, 기존 데이터는 보관된 테스트 결과로 채움) |

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
| 7 | 동시 채점 수 제한(세마포어) + 접수 제한(대기열 상한·유저별 진행 중 개수) + Docker 호출 시간 제한, 동시 제출 테스트로 6단계 측정값과 비교 (7장 "부하 제한") | 완료 (값은 임시, `docs/tuning.md`) |
| 8 | 부하 테스트 도구(k6 등, 사전 검토 후) 도입: 실제 서버에 가상 유저로 응답 시간 분포 측정 (배포 전) | 예정 |
| 라운지 1 | 라운지 혼자 걷기: Phaser 지도, 도트 고양이(4방향 걷기·앉기), 충돌, 책상 앞 `[F]` 앉기 → 문제 선택, ESC·[일어나기], 화면을 옮겨도 지도 유지 (10장) | 완료 |
| 라운지 2 | 여러 명 동기화: STOMP, 접속·퇴장, 위치·상태 방송, 접속자 수, 책상 점유, 게스트 신원 (10장 "2단계") | 완료 (브라우저 탭 두 개로 입장·이동·책상 점유·퇴장 확인) |
| 라운지 3 | 활동 표시: 타이핑 중(앞발·말풍선), 낮잠 중(z, 노트북 꺼짐), 제출 AC 축하(내 SOLVED!, 남의 별, 전광판, 방금 푼 고양이), 오답 ...(임시), 옆자리 목록, 접힌 띠 (10장 "3단계") | 완료 |
| 라운지 4 | 문제 목록 [완료] 뱃지 + 코드 관전(Yjs, Spring 단순 중계, 읽기 전용 강제, 주인 커서 표시, 관전자 수) (10장 "관전") | 완료 |
| 라운지 5 | 페어 프로그래밍(요청·수락, 상대 1명 쓰기 허용, 서로 커서·이름표, 1:1 채팅) + 라운지 전체 채팅(게스트 허용, 머리 위 말풍선, 도배 제한) (10장 "페어") | 완료 |

---

## 10. 라운지(메타버스) 설계

### 흐름
```
라운지(/)에서 WASD·방향키로 걷기
 → 빈 책상 앞(앞판 아래 10도트 안)에 서면 [F] 책상에 앉기 툴팁
 → F: 앉음 (노트북 화면이 켜짐) → 문제 선택(/problems), 지도는 왼쪽 280px 패널로 줄고 내 고양이를 가운데에 둠
 → 문제 클릭 → 풀이(/problems/1)에서도 같은 지도가 그대로
 → ESC 또는 [일어나기] → 책상 앞에 서서 라운지로
```
- 앉은 적 없이 문제 주소로 바로 들어오면(새 탭, 링크) 가운데 책상(7번)에 앉힌다.
- 브라우저 뒤로 가기로 라운지에 돌아오면 일어난다.
- 앉은 책상은 탭 단위(sessionStorage)로, 서 있던 자리와 털색은 브라우저 단위(localStorage)로 기억한다. 로그인하러 떠났다 와도 같은 자리에 선다.

### 구조
| 무엇 | 위치 | 하는 일 |
|---|---|---|
| 공통 레이아웃 | `app/layouts/LoungeLayout.tsx` | 상단 바 + 지도 + 자식 화면(`Outlet`). 주소와 "앉아 있는가"를 맞춤 |
| 지도 자리 | `widgets/lounge-map/ui/LoungeMapArea.tsx` | 라운지 = 가운데 전체, 앉은 화면 = 왼쪽 패널(접기, [일어나기], ESC) |
| 캔버스 + 툴팁 | `widgets/lounge-map/ui/LoungeMap.tsx` | Phaser 게임을 한 번만 만들고(동적 import), 그 위에 React로 `[F]` 툴팁 |
| 장면 | `widgets/lounge-map/lib/LoungeScene.ts` | 방·책상·장식 배치, 이동·충돌, 근처 책상 찾기, 앉기·일어나기, 카메라 |
| 지도 데이터 | `widgets/lounge-map/model/loungeMap.ts` | 320x180 도트, 책상 15개(3줄 x 5), 창문·전광판·러그·장식 좌표 |
| 고양이 도트 | `entities/avatar/lib/catSprite.ts` | 도트 템플릿 → 스프라이트 시트. 털색은 팔레트만 바꿈 |
| 외형 | `entities/avatar/model/appearance.ts` | `{ coat }`. 꾸미기 아이템이 생기면 칸을 늘림 |
| 공유 상태 | `entities/lounge/model/loungeStore.ts` | 화면 모드, 근처 책상, 앉은 책상, 지도 접힘 |

### 공부할 개념

**1. 게임 루프와 React의 역할 나누기**
- 게임은 1초에 약 60번 "입력 읽기 → 위치 옮기기 → 충돌 검사 → 다시 그리기"를 반복한다(게임 루프). Phaser 장면의 `update()`가 매 프레임 불린다.
- React는 상태가 바뀔 때 다시 그리는 방식이라 1초에 60번 리렌더하면 무겁다. 그래서 **고양이 좌표처럼 매 프레임 바뀌는 값은 Phaser 안에만** 두고, React는 가끔 바뀌는 값(근처 책상, 앉은 책상)만 본다.
- 맵은 Phaser가 캔버스에 직접 그리고, 상단 바·툴팁·버튼·에디터는 React가 그린다.

**2. 외부 저장소와 `useSyncExternalStore`**
- `loungeStore`는 React 바깥의 작은 저장소다(`getState`, `subscribe`, 값을 바꾸는 함수들). Phaser도 React도 이것만 읽고 쓴다. 서로를 직접 부르지 않아서, 한쪽을 바꿔도 다른 쪽이 덜 깨진다.
- React는 `useSyncExternalStore(subscribe, getState)`로 구독한다. React 18의 동시성 렌더링에서 한 번의 렌더 안에 컴포넌트마다 다른 값을 보는 문제(tearing)를 막아 준다. Redux·Zustand도 안에서 이 훅을 쓴다.
- 흐름 예: 고양이가 책상 앞에 섬 → 장면이 `setNearbyDesk({deskId: 7, ...})` → 저장소가 구독자에게 알림 → React 툴팁이 뜸 → F → 장면이 `sit(7)` → 레이아웃이 보고 `/problems`로 이동 → 장면도 같은 알림을 받아 고양이를 앉힘.

**3. 화면을 옮겨도 게임을 유지하기 (레이아웃 라우트)**
- React Router의 레이아웃 라우트: 부모 라우트(`/`, `LoungeLayout`)가 자식 화면(`/problems`, `/problems/:id`)을 `Outlet` 자리에 바꿔 끼운다. 부모 안의 지도는 화면이 바뀌어도 사라지지 않는다.
- 페이지마다 게임을 만들고 지우면 그때마다 에셋을 다시 읽고(2단계에서는 연결도 다시 맺고) 앉아 있던 고양이도 다시 생긴다. 캔버스 DOM을 다른 상자로 옮겨 붙이는 방법은 React가 모르는 DOM 조작이라 버그가 나기 쉽다.
- 주의: React는 자식을 **순서**로 맞춰 본다. 지도 앞의 조건부 요소를 `조건 ? <요소> : null`로 두면 빈 자리(`null`)가 남아 지도가 늘 같은 순서에 있다. 순서가 바뀌면 React가 지도를 새로 만들어 게임이 다시 시작된다 (`LoungeMapArea` 주석).
- 크기만 CSS로 바뀌고, Phaser의 `Scale.RESIZE`가 상자 크기에 맞춰 캔버스를 다시 맞춘다. 접으면 `game.loop.sleep()`으로 그리기를 멈춘다.
- 개발 모드의 StrictMode는 효과(`useEffect`)를 한 번 지웠다가 다시 실행한다. 지울 때 `game.destroy(true)`를 하지 않으면 캔버스가 두 개 생긴다.

**4. 동적 import로 번들 나누기**
- `LoungeMap`이 `import('.../startLoungeGame')`으로 Phaser를 부른다. Vite가 Phaser(약 1.3MB)를 별도 파일로 떼어 내서, 지도가 처음 뜰 때만 내려받는다. 정적 `import Phaser`는 이 파일과 장면 파일에만 있다.

**5. 도트 그림: 정수배 확대와 `pixelArt`**
- 지도는 320x180 도트로 그리고 카메라 `zoom`으로 키운다. 라운지는 "방 전체가 들어가는 가장 큰 정수배", 패널은 2배. 정수배여야 도트 하나가 화면 픽셀 n x n으로 고르게 커진다(3.5배면 어떤 도트는 3칸, 어떤 도트는 4칸이 된다).
- `pixelArt: true`: 확대할 때 주변 색을 섞지 않고 가장 가까운 픽셀을 그대로 쓴다(nearest-neighbor). 이게 없으면 도트가 뿌옇게 번진다.
- 이름표 글자는 `setResolution(zoom × devicePixelRatio)`로 미리 촘촘하게 그려 둔다. 그대로 키우면 글자가 깍두기처럼 깨진다.

**6. 탑다운의 앞뒤 순서 (y-정렬)**
- 위에서 내려다보는 화면에서는 **발이 아래(y가 큰) 쪽에 있는 것이 앞**이다. 고양이와 책상·장식의 depth를 "발(바닥에 닿는 곳)의 y"로 매긴다.
- 그래서 고양이 위치(Container의 원점)를 발에 두고, 부딪히는 몸통도 발밑 8x4만 둔다. 머리는 책상이나 화분 뒤로 자연스럽게 지나간다.
- 앉은 고양이는 상반신과 앞발만 그린 프레임을 책상보다 앞에 둔다. 아래 몸이 시트에 없으니 책상에 가려진 것처럼 보이고, 앞발은 책상 위에 얹힌다.

**7. 간단 물리 (Arcade Physics)**
- 벽·책상·장식은 움직이지 않는 몸통(static body), 고양이는 움직이는 몸통이다. `collider`가 둘이 겹치지 않게 밀어낸다.
- 대각선 이동은 x, y 속도를 그대로 더하면 √2배 빨라진다. 방향 벡터의 길이를 1로 맞춘 뒤 속도를 곱한다.

**8. 같은 템플릿 + 팔레트 = 털색 (나중에 꾸미기)**
- 고양이는 글자 격자 템플릿(`f` 털, `w` 주둥이, `e` 눈 ...)으로 그리고, 글자마다 어떤 색 토큰을 쓸지를 털색별 팔레트로 정한다. 그림 하나로 털색 5종이 나온다. Phaser의 `tint`(색 곱하기)는 한 가지 색만 입힐 수 있어서 치즈 털 + 크림색 주둥이 같은 여러 색 무늬를 못 만든다.
- 시트 배치 규칙(줄 = 자세, 칸 = 프레임, 칸 16x16, 발은 칸 아래 가운데)만 지키면 그림을 더 자세히 그려도 게임 코드는 그대로다.
- 고양이는 처음부터 레이어 묶음(`Container`)이다. 모자·안경을 같은 칸 격자로 그려 더하면 꾸미기가 된다. 외형은 `{ coat }` 같은 데이터라 2단계에서 다른 사람에게도 그대로 보낼 수 있다.
- 색은 `tokens.css`의 `--map-*`, `--cat-*`에서 읽는다. 캔버스 `fillStyle`은 `oklch(...)` 문자열을 그대로 받는다. 지도와 IDE가 같은 팔레트라 한 공간처럼 보인다.

**9. 키 입력과 입력칸**
- Phaser 키에 `enableCapture: false`를 줬다. 켜 두면 Phaser가 W·A·S·D·F의 기본 동작을 막아서 에디터·입력칸에 그 글자를 칠 수 없다.
- 고양이는 라운지 화면이고, 서 있고, 입력칸에 포커스가 없을 때만 움직인다. ESC도 입력칸·에디터 밖에서만 [일어나기]로 쓴다(Monaco가 자동 완성 닫기에 ESC를 먼저 쓴다).

### 선택과 이유
| 항목 | 선택 | 이유 (대안) |
|---|---|---|
| React 연결 | 공통 레이아웃에 지도 고정 + 외부 저장소 | 페이지마다 생성·제거(재로딩·재연결), 캔버스 옮겨 붙이기(React 밖 DOM 조작) |
| 지도 정의 | 코드 데이터(`loungeMap.ts`) | 방 하나·책상 15개라 Tiled 편집기 없이 파일 하나로 보인다. 방이 늘면 Tiled JSON |
| 바닥·벽·책상·노트북 | 직접 그림 (목업 도트, 테마 토큰 색) | Kenney 실내 팩에 바닥·벽·노트북이 없고, 목업의 차콜·나무 팔레트와 맞춰야 함 |
| 장식 | Kenney Roguelike Indoors (CC0) 화분·선반 | 출처 표기 의무 없음, 상업 이용 가능. `frontend/public/assets/CREDITS.md` |
| 고양이 | 직접 그림 (목업 10x10 도트, 4방향·앉기) | 위에서 본 4방향 걷기가 있는 CC0 고양이를 찾지 못함 (옆모습뿐이거나 유료) |
| 털색 | 템플릿 + 팔레트 교체 | `tint`는 한 색만 입힘 |
| 이동 속도·애니메이션 | 초당 45도트, 걷기 초당 4칸 | 화면(4배)에서 초당 약 180px. 임시값 (`docs/tuning.md`) |

### 한계 (다음에 고칠 것)
- 이름표가 둥근 알약이 아니라 네모 배경이다(Phaser `Text`의 배경). 목업처럼 둥글게 하려면 캔버스 텍스처로 직접 그린다.
- 패널 아래 빈 자리는 2단계 "옆자리" 목록(누가 작성 중인지) 자리다.

### 2단계: 여러 명 동기화 (STOMP)

#### 흐름
```
라운지를 열면 ws://(같은 주소)/ws 로 WebSocket 연결 (Vite 가 8080 으로 넘김, 로그인 쿠키가 자동으로 붙음)
 → 서버가 핸드셰이크에서 신원을 정함: 쿠키가 맞으면 회원(DB 닉네임), 없거나 틀리면 게스트
 → 구독 2개: /topic/lounge (모두에게 방송), /user/queue/lounge (나에게만)
 → /app/lounge/join (위치·털색·게스트 닉네임·앉은 책상)
   → 나에게 snapshot(지금 있는 모두 + 내 아바타 id), 다른 사람에게 joined
 → 걸으면 /app/lounge/move (최대 100ms에 한 번 + 멈출 때 한 번) → 모두에게 moved
 → F → /app/lounge/sit → 비어 있으면 모두에게 sat(그때 실제로 앉음), 차 있으면 나에게 sit-rejected
 → ESC → /app/lounge/stand → stood
 → 탭을 닫으면 연결 끊김 → 서버가 지우고 left
```
- 상단 바에 `공용 라운지에 N마리 접속`. 연결이 없으면 숫자를 숨긴다.
- 끊기면 3초 뒤 자동으로 다시 연결하고, 지금 위치·책상으로 다시 입장한다. 로그인 후 새로고침도, 로그아웃 후 재연결도 같은 길이다.

#### 구조
| 무엇 | 위치 | 하는 일 |
|---|---|---|
| STOMP 설정 | `backend/.../lounge/LoungeWebSocketConfiguration` | `/ws`, 허용 출처, simple broker, 메시지 순서 보장 |
| 신원 정하기 | `LoungeHandshakeHandler`, `LoungeIdentity` | 핸드셰이크 때 쿠키 → JWT 검증 → 회원/게스트. 연결마다 아바타 id |
| 접속자 목록 | `LoungeRegistry` | 서버 메모리. 아바타 상태, 책상 주인. 입력 바로잡기(지도 밖 좌표, 없는 털색, 회원 닉네임 흉내) |
| 메시지 처리 | `LoungeController` | `@MessageMapping` join/move/sit/stand, 연결 끊김 이벤트 |
| 메시지 모양 | `LoungeMessages` ↔ `frontend/src/entities/lounge/api/loungeMessages.ts` | 두 쪽이 같아야 한다 |
| 연결 모듈 | `frontend/src/entities/lounge/api/loungeConnection.ts` | 연결·재연결·입장, 위치 보내기, 앉기 요청, 앉기·일어나기를 서버에 한 번만 알림 |
| 접속자 저장소 | `frontend/src/entities/lounge/model/rosterStore.ts` | 서버가 알려 준 모두. Phaser 는 매 프레임 읽고, React 는 접속자 수만 구독 |
| 다른 사람 고양이 | `frontend/src/widgets/lounge-map/lib/RemoteCats.ts` | 그리기, 보간, 앉은 자세, 노트북 켜기 |

#### 공부할 개념

**1. WebSocket과 STOMP**
- HTTP는 "요청하면 한 번 응답"이라 서버가 먼저 말을 걸 수 없다. 다른 사람이 움직였다는 소식을 받으려면 계속 물어봐야 한다(폴링). WebSocket은 한 번 열면 양쪽이 아무 때나 보낼 수 있는 통로다.
- WebSocket은 통로만 있고 "어떤 형식으로 말할지"가 없다. STOMP는 그 위의 메시지 규칙이다. `SUBSCRIBE`(이 주소로 오는 것을 받겠다), `SEND`(이 주소로 보낸다), `MESSAGE`(구독한 주소로 온 것)라는 프레임과 주소(destination)가 있다.
- Spring의 simple broker는 "누가 어느 주소를 구독했는가"를 서버 메모리에 두는 내장 우체국이다. `/app/...`으로 온 것은 컨트롤러(`@MessageMapping`)가 받고, 컨트롤러가 `/topic/...`으로 보내면 구독자 모두에게 간다.

**2. 핸드셰이크에서 신원 정하기**
- WebSocket은 처음에 평범한 HTTP 요청(`Upgrade: websocket`)으로 시작한다. 브라우저는 이 요청에 쿠키를 자동으로 붙인다. 그래서 HttpOnly 쿠키(자바스크립트가 못 읽음)로도 인증할 수 있다.
- 신원은 이때 **한 번만** 정해 연결에 붙여 둔다(`Principal`). 메시지를 처리할 때는 본문에 적힌 id가 아니라 이 신원만 쓴다. 본문의 id를 믿으면 누구나 `{"userId": 7}`이라고 적어 남의 고양이를 움직일 수 있다.
- 그래서 로그인·로그아웃으로 쿠키가 바뀌면 연결을 다시 열어야 새 신원이 붙는다 (`loungeConnection.restart()`).
- 원래 쓰던 `BearerTokenResolver`는 `/ws`에서 쿠키를 읽지 않게 했다. 읽으면 만료된 쿠키를 가진 사람은 `permitAll`이어도 토큰 검증 실패로 401이 나서 게스트로도 들어오지 못한다.

**3. 방송과 개인 큐**
- `/topic/lounge`: 구독한 모두에게 (누가 들어왔다, 움직였다, 앉았다).
- `/user/queue/lounge`: 이 연결 하나에게만 (입장 직후 전체 목록, 책상 거절). 클라이언트는 `/user/queue/lounge`를 구독하고, 서버는 `convertAndSendToUser(신원 이름, "/queue/lounge", ...)`로 보낸다. 신원 이름(`Principal.getName()`)을 연결마다 다른 아바타 id로 둬서, 같은 회원이 탭을 두 개 열어도 각 탭이 자기 것만 받는다.

**4. 다른 사이트에서 몰래 연결하기 (Cross-Site WebSocket Hijacking)**
- 공격자 사이트가 방문자의 브라우저로 우리 `/ws`를 열면, 브라우저가 우리 쿠키를 붙일 수 있다. 그러면 공격자 페이지가 방문자 이름으로 라운지에 들어온다.
- 막는 방법: 서버가 핸드셰이크의 `Origin` 헤더를 보고 허용한 프론트 주소(`app.frontend-url`)가 아니면 거절한다(`setAllowedOrigins`). 쿠키의 `SameSite=Lax`도 다른 사이트에서 시작한 요청에 쿠키를 덜 붙인다. 테스트 `rejectsOtherOrigins`가 확인한다.

**5. 메시지 순서 (`setPreserveReceiveOrder`, `setPreservePublishOrder`)**
- Spring은 받은 메시지를 여러 스레드로 나눠 처리한다. 그대로 두면 한 사람이 보낸 이동 1, 2, 3이 2, 1, 3 순서로 처리되어 다른 사람 화면에서 고양이가 뒤로 튈 수 있다.
- 두 설정은 "같은 연결에서 온 것은 받은 순서대로 처리하고, 같은 연결로 가는 것은 보낸 순서대로 보낸다"를 보장한다. 다른 연결끼리는 여전히 동시에 처리되어 느려지지 않는다.

**6. 동시에 같은 책상 (원자적 연산)**
- 두 사람이 같은 순간 같은 책상에서 F를 누르면, "비어 있나 보고 → 앉힌다"를 두 번 하면 둘 다 "비었다"를 보고 둘 다 앉을 수 있다(check-then-act, 7장 "부하 제한"과 같은 문제).
- `ConcurrentHashMap.putIfAbsent(책상, 나)`는 "비어 있을 때만 넣기"를 한 번에 해서 한 명만 성공한다. 그래서 클라이언트도 바로 앉지 않고, 서버의 `sat` 방송을 받은 뒤에 앉는다. 서버가 꺼져 있으면 혼자 앉는다.
- 앉은 채로 다시 연결했는데 그새 남이 그 책상에 앉았거나, 문제 주소로 바로 들어왔는데 기본 책상이 차 있으면 가까운 빈 책상으로 옮긴다(`firstFreeDesk`).

**7. 보내는 양 줄이기 (throttle)와 받는 쪽 보간**
- 게임 루프는 초당 60번 돌지만 그걸 다 보내면 30명일 때 서버가 초당 54,000개를 뿌려야 한다. 움직일 때만, 최대 100ms에 한 번 보내고, 멈추는 순간 마지막 위치를 한 번 더 보낸다(이게 없으면 남의 화면에서 멈추기 직전 자리에 남는다).
- 받는 쪽은 100ms마다 순간이동하지 않도록, 매 프레임 남은 거리의 `(지난 시간 / 100ms)`만큼 다가간다(선형 보간). 40도트보다 멀면(재연결 등) 바로 옮긴다.
- 위치는 클라이언트를 믿는다. 서버는 지도 밖 좌표만 잘라 낸다. 벽 통과 같은 조작은 막지 못하지만, 라운지는 점수가 걸린 곳이 아니라 허용했다.

**8. 서버 메모리 상태의 한계**
- 접속자 목록은 DB가 아니라 서버 메모리(`ConcurrentHashMap`)다. 위치는 초당 여러 번 바뀌고, 서버가 꺼지면 연결도 다 끊겨 어차피 다시 입장하므로 잃어도 된다.
- 서버가 여러 대가 되면 서버마다 다른 목록을 갖고, simple broker도 자기 서버의 구독자에게만 보낸다. 그때는 외부 브로커(RabbitMQ STOMP 릴레이 등)와 공용 저장소(Redis)로 옮긴다.

#### 선택과 이유
| 항목 | 선택 | 이유 (대안) |
|---|---|---|
| 통신 | WebSocket + STOMP, Spring simple broker | 서버 1대라 내장 우체국으로 충분. 주소·구독 규칙을 직접 만들 필요가 없다 (Socket.IO: Spring 공식 지원 없음, 직접 만든 WebSocket 규칙: 구독·개인 큐를 다 만들어야 함) |
| SockJS | 쓰지 않음 | WebSocket이 막힌 환경용 대체 통신. 지원 브라우저가 모두 WebSocket을 쓴다 |
| 인증 | 핸드셰이크 쿠키 | STOMP CONNECT 헤더에 토큰을 넣으려면 자바스크립트가 토큰을 읽어야 하는데 HttpOnly라 못 읽는다 |
| 게스트 신원 | 연결마다 서버가 아바타 id 발급, 닉네임·털색은 localStorage | 게스트는 저장할 기록이 없다. 위조할 수 없고 단순하다 |
| 같은 회원 탭 두 개 | 고양이 두 마리 | 한 마리로 합치려면 "어느 탭이 진짜인가"를 정해야 한다. 나중에 |
| 접속자 목록 | 서버 메모리 | 잃어도 되는 값, 초당 여러 번 바뀜 |
| 클라이언트 | `@stomp/stompjs` 7.5.0 | 자동 재연결 내장, 의존성 없음 |

#### 검증
- `LoungeWebSocketTest` (실제 서버에 STOMP 클라이언트 여러 개, Docker 필요)
  - 두 게스트가 서로 보이고 이동이 전달된다
  - 같은 책상은 한 명만, 늦게 온 쪽은 거절, 먼저 앉은 쪽이 나가면 책상이 빈다
  - 쿠키가 맞으면 회원 닉네임, 틀리면 게스트
  - 회원 닉네임 흉내·없는 털색·지도 밖 좌표를 서버가 바로잡는다
  - 다른 출처(Origin)는 연결할 수 없다
- 브라우저 탭 두 개 (2026-11-11): 서로 입장하면 `2마리 접속`, 한쪽 위치·방향·멈춤이 다른 쪽에 도착, 한쪽이 3번 책상에 앉으면 다른 쪽 화면에 앉은 고양이와 켜진 노트북이 보이고 같은 책상 앉기는 거절, 탭을 닫으면 `1마리`로 줄고 책상이 빈다.

### 3단계: 활동 표시

#### 보이는 것
| 상태 | 맵 | 판단 |
|---|---|---|
| 타이핑 중 | 앉은 고양이 앞발이 번갈아 두드리고, 머리 왼쪽 말풍선의 점 3개가 차례로 복숭아색 | 에디터에 마지막으로 입력한 뒤 5초 |
| 낮잠 중 | 눈을 감고 머리 옆에 `z`, 노트북이 꺼짐 | 앉은 채 5분 입력 없음 |
| 제출 AC | 내 화면: 이름표 자리에 `SOLVED!` / 남의 화면: 그 고양이(탭이 여럿이면 모두) 머리 위 별 10초, 벽 전광판 `OO님이 풀었어요`, 오른쪽 "방금 푼 고양이"(최근 5개) | 서버의 채점 결과 |
| 제출 오답 (임시) | 내 화면에서만 회색 `...` 말풍선 3초 | 내 화면의 채점 결과 |

- 지도 패널 아래 "옆자리": 앉은 사람을 내 책상에서 가까운 순서로, 작성 중·낮잠 중·방금 풀었음(5분)·앉아 있음.
- 지도를 접은 48px 띠: 접속 수, 작성 중인 수, 5분 안에 누가 풀었으면 별.

#### 흐름
```
타이핑: 에디터 글자 바뀜 → activityTracker.touch() → (idle 이었으면) typing 으로 바뀜 → /app/lounge/activity 한 번
        → 5초 입력 없으면 idle 로 바뀜 → 한 번 더. 키 하나마다 보내지 않는다
SOLVED: 제출 채점 AC → 결과 저장(커밋) → SubmissionAcceptedEvent → LoungeController 가 닉네임·문제 제목을 붙여
        /topic/lounge 로 solved(그 회원의 아바타 id들, 기록) → 모든 화면이 별·전광판·목록을 갱신
```

#### 공부할 개념

**1. "누가 풀었나"는 서버만 정한다 (server-authoritative)**
- 클라이언트가 "나 풀었어"를 보내게 하면 개발자 도구로 누구나 가짜 축하를 띄울 수 있다. 그래서 축하는 서버의 채점 결과(AC)에서만 나온다. 반대로 타이핑·낮잠은 틀려도 피해가 없어 클라이언트가 판단한다.

**2. 이벤트로 기능 사이를 끊기 (`ApplicationEventPublisher`)**
- 채점 작업자는 `SubmissionAcceptedEvent`만 남기고, 누가 듣는지 모른다. 라운지는 `@EventListener`로 듣는다. 채점 코드가 라운지 코드를 직접 부르지 않아, 라운지를 빼거나 고쳐도 채점은 그대로다(느슨한 결합).
- 이벤트는 결과 저장이 커밋된 **뒤**, 채점의 `try` **밖**에서 남긴다. 안에서 남기면 라운지 쪽 오류가 `catch`로 넘어가 채점 결과가 `FAILED`로 바뀐다.

**3. 바뀔 때만 보내기 (edge-triggered)**
- "지금 타이핑 중"을 키마다(level) 보내면 1분에 수백 개다. "idle → typing", "typing → idle"처럼 상태가 **바뀌는 순간**(edge)에만 보내면 몇 개로 끝난다. 다시 연결하면 서버는 idle로 시작하므로 입장 직후 지금 상태를 한 번 더 보낸다.

**4. 하트비트 (STOMP heart-beat)**
- 브라우저가 인사 없이 사라지면(와이파이 끊김, 프록시 사이에서 끊김) TCP 연결만 남아 서버는 끊긴 줄 모른다. 고양이가 유령처럼 남는다.
- 양쪽이 10초마다 "살아 있음" 프레임을 주고받고, 신호가 끊긴 연결은 서버가 닫는다(`setHeartbeatValue` + `TaskScheduler`). 닫히면 기존 연결 끊김 처리가 `left`를 방송한다. `@stomp/stompjs`는 기본으로 10초 하트비트를 보낸다.

**5. StrictMode와 늦게 도착하는 콜백 (실제로 겪은 버그)**
- 개발 모드 StrictMode는 효과를 "시작 → 정리 → 다시 시작"한다. 첫 연결을 정리할 때의 "끊김" 콜백이 두 번째 연결의 "연결됨"보다 **늦게** 도착하면, 살아 있는 두 번째 연결의 접속자 목록을 비워 버렸다(가끔만 재현).
- 고침: 각 콜백이 자기를 만든 클라이언트가 지금의 클라이언트일 때만 상태를 바꾼다(`if (client !== created) return`). 비동기 콜백에서 "이 결과가 아직 유효한가"를 확인하는 일반적인 방법이다.

**6. React가 필요한 것만 다시 그리기 (선택적 구독)**
- 접속자 저장소는 누가 걸을 때마다 바뀐다(1명당 초당 10번). 목록 컴포넌트가 저장소 전체를 구독하면 그때마다 다시 그린다.
- `useSeatedNeighbors`는 "앉은 사람 정보"만 뽑고, 내용이 같으면 **같은 배열 객체**를 돌려준다. `useSyncExternalStore`는 값이 같은 객체면 다시 그리지 않는다.

**7. 렌더링 중 시간 읽지 않기 (`useNow`)**
- "5분 안에 풀었으면 방금 풀었음"을 렌더링 중 `Date.now()`로 계산하면, 같은 상태인데도 그릴 때마다 결과가 달라지고(순수하지 않음) 시간이 지나도 다시 그려지지 않아 표시가 남는다. 30초마다 바뀌는 시각을 저장소에 두고 구독한다.

**8. Phaser 4 마스크**
- 전광판 글자를 흘리려고 `setMask`(지오메트리 마스크)를 썼더니 Phaser 4 WebGL 렌더러는 지원하지 않는다는 경고가 났다(4에서 마스크가 필터 방식으로 바뀜). 흐르는 자막 대신 `OO님이 풀었어요`를 띄우고, 길면 글자 크기를 줄여 전광판에 맞췄다. 문제 제목은 오른쪽 목록에 나온다.

#### 검증
- `LoungeWebSocketTest`에 2개 추가 (모두 7개): 앉은 고양이의 활동만 방송·일어나면 idle / AC 이벤트 → 그 회원 아바타에 `solved` 방송, 나중에 들어온 사람도 최근 기록을 받음
- 브라우저 탭 두 개 (2026-11-11): 탭 B 타이핑 → 탭 A에 말풍선, 탭 B가 실제 제출 AC → 탭 A 전광판·목록·별, 탭 B 자기 화면 `SOLVED!`. 실패 `...`는 그리기만 확인(자동 클릭이 에디터 준비 전에 눌려 실제 오답 제출 흐름은 확인하지 못함)

### 4단계: 코드 관전 (Yjs + Spring 단순 중계)

#### 흐름
```
주인이 풀이 화면을 엶 → /app/desk/host (문제·언어) → 서버가 무작위 방 id(d-xxxxxxxx)로 방을 열고 주인에게 hosted
관전 시작: 라운지에서 남이 앉은 책상 앞 [F] "OO님 코드 구경하기" 또는 옆자리 [관전] → /spectate/주인 아바타 id
 → /app/desk/{주인 아바타 id}/join → 관전자에게 opened(실제 방 id·문제·언어·닉네임), 주인에게 member-joined(관전자 id)·spectators(수)
 → 관전 화면 주소를 /spectate/{방 id} 로 바꿈 (이 주소를 공유하거나 새로고침해도 같은 방)
 → 주인 브라우저가 지금 문서 전체를 그 관전자에게만 (/full) → 관전자의 읽기 전용 에디터에 코드가 나타남
주인이 칠 때마다 → Yjs update(바뀐 부분) + 커서(awareness) → /update → 방의 관전자들에게
주인이 일어나거나 나감 → 방 닫힘 → 관전자에게 closed "자리를 떠났어요"
```
- 주인 화면 에디터 머리에 `N명이 구경하고 있어요`. 관전자가 없으면 주인은 아무것도 보내지 않는다.
- 관전 화면: 배너, 주인의 문제 지문, 읽기 전용 에디터(주인 커서 표시, 드래그해서 복사 가능), [나가기]·ESC. 라운지에서 시작했으면 내 고양이는 서 있고, 내 책상에서 시작했으면 앉은 채로.
- 주인이 문제·언어를 바꾸면 방을 새 문서로 다시 열고(reset) 관전자 화면도 따라 바뀐다.

#### 공부할 개념

**1. CRDT와 Yjs**
- 여러 사람이 같은 글을 동시에 고칠 때 "마지막에 온 것으로 덮어쓰기"를 하면 한 사람의 입력이 사라진다. CRDT(충돌 없는 복제 데이터 타입)는 글자마다 "누가, 어느 글자 뒤에" 넣었는지 고유 id를 붙여, 변경이 어떤 순서로 도착해도 모두 같은 결과로 합쳐진다. 서버가 순서를 정해 줄 필요가 없다.
- OT(Operational Transformation, 구글 문서 초기 방식)는 서버가 순서를 정하고 늦게 온 변경을 변환한다. 서버가 똑똑해야 해서 Spring 단순 중계로는 안 된다(ShareDB는 Node 서버 필수).
- Yjs 구성: `Y.Doc`(공유 문서, 책상 하나) / `Y.Text`(코드) / update(바뀐 부분을 담은 작은 바이트) / awareness(커서·선택 영역처럼 저장하지 않는 지금 상태) / `y-monaco`(Monaco ↔ `Y.Text` 연결, 남의 커서를 그려 줌).
- 관전은 "읽기 전용 페어"다. 페어 프로그래밍을 붙일 때 같은 문서·같은 중계를 쓰고, 상대의 변경도 받아 주기만 하면 된다.

**2. 단순 중계 (서버는 바이트 내용을 모른다)**
- 서버가 하는 일: 방 열고 닫기, 입장 자격, 읽기 전용 강제, 같은 방 사람에게 전달, 관전자 수 알림. 하지 않는 일: 코드 합치기(브라우저 Yjs), 문서 저장(주인 브라우저에 늘 최신 문서가 있다).
- 늦게 들어온 관전자: 서버가 주인에게 "X가 들어왔다"를 알리면 주인이 `Y.encodeStateAsUpdate(doc)`(문서 전체)를 X에게만 보낸다. 그 뒤로는 바뀐 부분만. 서버에 사본이 없어도 된다.
- 바이트는 base64 문자열로 바꿔 JSON STOMP 메시지에 싣는다(약 33% 커지지만 디버깅이 쉽다).

**3. 방송(/topic) 대신 개인 큐로 한 명씩**
- `/topic/desk/7`은 아무나 구독할 수 있어 "방에 들어온 사람만 받기"를 서버가 지킬 수 없다(구독 가로채기를 따로 만들어야 함). 개인 큐(`/user/queue/desk`)로 서버가 방 명단을 보고 한 명씩 보내면 명단 밖 사람은 받을 길이 없다. 방 인원이 몇 명이라 비용이 작다. 테스트 `relaysOwnerDocumentToSpectators`가 방 밖 사람이 못 받는 것을 확인한다.

**4. 읽기 전용은 서버에서 강제한다**
- 관전자 에디터는 `readOnly`지만, 개발자 도구로 `/update`를 보내면 주인 코드가 바뀔 수 있다. 서버는 방 주인이 보낸 `update`·`full`만 받아 주고 나머지는 버린다(`DeskRooms.ownedBy`). 화면의 제한은 편의, 서버의 제한이 보안이다.

**5. 메시지 크기 제한은 여러 겹이다**
- 관전 시작 때 코드 전체(최대 64K자, base64로 더 커짐)가 메시지 하나로 간다. 막는 곳이 세 군데였다: STOMP 메시지 크기(기본 64KB, `setMessageSizeLimit`), Tomcat WebSocket 수신 버퍼(기본 8KB, `ServletServerContainerFactoryBean`), 테스트용 Java STOMP 클라이언트의 수신 제한(64KB). 512KB로 올렸고 테스트가 200KB 문서를 보낸다. 브라우저에는 이런 제한이 없다.

**6. 오래된 라이브러리와 새 Monaco (모듈 경로 별칭)**
- `y-monaco` 0.1.6(2024)은 Monaco 내부 경로 `monaco-editor/esm/vs/editor/editor.api.js`를 불러오는데, Monaco 0.57은 패키지 구조가 바뀌어 그 경로가 없다(빌드 실패). Vite `resolve.alias`로 그 경로를 정식 진입점 `monaco-editor`로 돌렸다. 앱과 같은 Monaco 하나를 함께 쓰게 되어 오히려 맞다.
- `y-monaco`는 연결할 때 에디터 내용을 `Y.Text`로 덮어쓴다. 그래서 주인은 문서에 지금 코드를 먼저 넣고(`text.insert(0, model.getValue())`) 연결한다. 관전자는 빈 문서로 연결하고 주인의 `full`을 기다린다.

**7. React에서 외부 객체 정리 순서**
- 관전 훅은 `Y.Doc`·`Awareness`(문서)와 `MonacoBinding`(에디터 연결)을 따로 만든다. 문서를 먼저 지우면 연결이 지워진 문서의 이벤트를 끊으려다 꼬일 수 있다. 효과(`useEffect`)의 정리는 선언 순서대로 돌므로, 연결 정리 효과를 먼저, 문서 정리 효과를 뒤에 선언했다. React 상태로 받은 객체를 직접 고치지 않는다(린트 `react(immutability)`).

#### [완료] 뱃지
- `GET /api/problems`는 로그인 없이도 열려 있다. 토큰(쿠키)이 있으면 `@AuthenticationPrincipal Jwt`에 신원이 들어오고, 게스트면 `null`이다. 로그인했으면 `user_solved_problems`에서 그 유저의 푼 문제 id를 한 번에 읽어(`findProblemIdsByUserId`, PK 앞 칼럼 `user_id`로 그 유저 행만) 목록에 `solved`를 붙인다.
- 프론트는 제출이 AC로 끝나거나 로그아웃하면 목록 캐시를 무효화해 뱃지를 다시 받는다.

#### 검증
- `LoungeWebSocketTest`에 2개 추가 (모두 9개): 방 정보·문서 전체(200KB)·변경이 관전자에게만, 방 밖 사람은 못 받음 / 관전자 변경은 버림, 주인이 나가면 closed, 없는 방은 not-found
- `ApiTest#marksSolvedProblems`: 로그인 유저가 푼 문제만 `solved`, 게스트는 모두 `false`
- 브라우저 탭 두 개 (2026-11-11): 관전 탭에 주인 코드·지문·배너, 주인 화면 `1명이 구경하고 있어요`, 주인이 친 줄이 관전 탭에 바로 나타나고 주인 커서(복숭아색)가 보임, 주인이 일어나면 "자리를 떠났어요", 문제 목록 1번에 [완료]

### 5단계: 페어 프로그래밍 + 라운지 전체 채팅

#### 페어 흐름
```
관전 중 [페어 요청] (게스트는 [로그인하고 페어 요청]) → /app/desk/{주인}/pair-request
 → 주인 화면에 "OO님이 같이 풀자고 해요 [수락] [거절]"
 → [수락] /app/desk/pair-accept → 서버가 그 방의 페어 상대를 1명으로 정함 → 둘에게 pair-started
 → 상대 화면: 에디터가 쓰기 가능으로, 배너 "OO님 책상에서 함께 풀고 있어요", 오른쪽에 "둘만의 채팅"
 → 서로 치는 대로 합쳐짐(Yjs), 서로의 커서 끝에 이름표
 → 둘 중 누구든 [페어 끝내기] 또는 상대가 [나가기] → pair-ended, 상대는 다시 관전(읽기 전용)
 → 상대 연결이 끊김 → 주인 화면 "OO님 연결이 잠깐 끊겼어요" → 15초 안에 같은 회원이 다시 들어오면 페어 이어짐, 아니면 pair-ended
 → 주인이 일어나면 방이 닫히고 페어도 끝남. 회원 주인의 연결이 끊기면 15초 기다림 (아래 "재연결 유예")
```
- 제출은 주인만 (상대 화면에는 실행·제출 버튼이 없음). 상대가 고친 코드도 주인 브라우저의 작성 중 코드로 저장된다.
- 주인이 지도를 48px 띠로 접어 두었으면 띠에 ⇄ 가 깜빡인다(`entities/desk/model/pairRequestStore`). 수락·거절은 문제 바 아래 배너에서.
- [페어 요청] 버튼은 회원 주인의 방에서만, 주인 본인이 아닐 때만 보인다(`opened.pairable` 을 서버가 보는 사람마다 계산).

#### 전체 채팅 흐름
```
라운지에서 Enter → 왼쪽 아래 채팅 입력칸 (앉아 있으면 지도 패널 아래 "옆자리 | 채팅" 탭)
 → /app/lounge/chat → 서버가 검사(1~200자, 1초에 1개) → /topic/lounge 로 모두에게 chat
 → 모든 화면: 채팅 창에 한 줄 + 보낸 고양이 머리 위 말풍선 4초
거절되면 보낸 사람에게만 chat-rejected ("조금 천천히 보내 주세요", "200자까지")
새로 들어온 사람은 입장 때 최근 30개를 함께 받는다 (서버 메모리, 재시작하면 사라짐)
```

#### 공부할 개념

**1. 권한 상태 기계 (요청 → 수락 → 진행 → 끝)**
- 방마다 "요청 대기 명단"과 "페어 상대(최대 1명)"를 서버 메모리에 둔다(`DeskRooms`). 요청은 회원 주인의 방에서, 관전 중인 다른 회원만, 상대가 없을 때만. 수락은 요청 명단에 있는 사람만.
- 상대는 1명: `DeskRooms`의 메서드는 모두 `synchronized`라서 주인이 두 요청을 거의 동시에 수락해도 먼저 들어온 하나만 상대가 된다. 한 방의 여러 칸(관전자·요청·상대)을 함께 바꾸므로 칸마다 `ConcurrentHashMap`을 두는 것보다 잠금 하나가 어긋날 일이 없다. 방 몇 개·몇 명 규모라 잠금 경쟁도 작다.
- 쓰기 허용 검사가 "주인만"에서 "주인 또는 상대"로 넓어졌다(`writableBy`). 관전자의 변경은 여전히 버린다. 상대의 변경은 주인과 다른 관전자 모두에게 간다(보낸 사람만 빼고).

**2. 상대가 나가면 페어도 끝 (자원 정리)**
- 상대가 [나가기]를 누르면 바로, 연결이 끊기면 유예(15초) 뒤에 페어를 끝내고 주인에게 알린다. 끝내지 않으면 "없는 사람이 상대"로 남아 다른 사람이 요청할 수 없게 된다.

**2-1. 재연결 유예 (연결 id 와 사람 id 를 나누기)**
- 아바타 id 는 연결마다 새로 생긴다. 처음에는 방 id = 주인 아바타 id, 페어 상대 = 상대 아바타 id 였는데, 와이파이가 3초만 끊겨도 새 id 로 다시 연결돼 방이 닫히고 관전 주소가 바뀌고 페어가 끝났다.
- 그래서 "누구인가"(회원 id)와 "지금 어느 연결로 보내나"(아바타 id)를 나눴다. 방은 주인의 회원 id 로, 페어 상대도 회원 id 로 기억하고, 메시지는 그 회원의 지금 연결로 보낸다. 같은 회원이 탭을 둘 열면 가장 최근에 방을 열거나 들어온 탭이 그 회원을 대표한다.
- 연결이 끊기면 바로 정리하지 않고 "자리 비움"으로 두고 15초 뒤 정리를 예약한다(`DeskController.afterGrace`, `deskGraceScheduler`). 그 사이 돌아오면 이어 가고, 예약된 정리는 번호표(ticket)가 바뀌어 아무 일도 하지 않는다. 돌아왔다가 다시 끊긴 경우를 앞의 예약과 구분하려고 번호표를 쓴다.
- 주인이 자리를 비운 동안은 상대도 쓸 수 없다(`writableBy`, 화면도 읽기 전용). 문서 원본은 주인 브라우저에 있어서, 주인이 돌아오면 주인 문서 전체로 다시 맞추기(reset → full) 때문이다. 끊긴 동안 쓴 내용은 지금은 살리지 않는다.
- 게스트 주인은 기다리지 않는다. 게스트는 다시 연결되면 같은 사람인지 알 근거가 없고(쿠키·회원 id 없음), 코드는 브라우저 draft 에 남아 있어 잃는 것이 관전 화면뿐이다. 그래서 게스트 방은 페어도 안 된다.

**2-2. 방 id 를 무작위로, 방이 열린 동안만**
- 회원 id(`users.id`)를 그대로 주소에 쓰면 링크가 영구적이 되고(한 번 받은 사람이 언제든 다시 들어옴), 번호를 차례로 넣어 볼 수 있고, 가입자 수가 드러난다.
- 방을 처음 열 때 `d-` + 무작위 8자를 만들고, 방이 닫히면(일어나기, 유예 끝) 버린다. 유예 안의 재연결은 회원 id 로 같은 방을 찾아 같은 id 를 쓴다. 라운지에서는 주인 아바타 id 로 들어오고, 서버가 실제 방 id 를 알려 준다(`opened.target`).

**3. 동시 편집에서 문서 전체 보내기**
- 페어 중에도 새 관전자에게는 주인이 문서 전체를 보낸다. 상대의 변경은 이미 주인 문서에 합쳐져 있어서(CRDT) 주인 문서가 곧 최신이다. 서버에 사본이 필요 없는 구조가 그대로 유지된다.

**4. 커서 이름표 (awareness + 동적 CSS)**
- 각자 awareness에 `user: { name }`을 넣는다. `y-monaco`는 남의 커서에 `yRemoteSelectionHead-{clientID}` 클래스만 붙이고 이름은 그리지 않는다.
- awareness가 바뀔 때마다 `<style>`에 `.yRemoteSelectionHead-123::after { content: "이름" }` 규칙을 사람마다 만들어 넣는다(`entities/desk/lib/cursorLabels.ts`). 이름은 `JSON.stringify`로 따옴표·특수문자를 이스케이프해 CSS 문자열로 안전하게 넣는다.

**5. 도배 제한은 서버가**
- 게스트도 쓰는 채팅이라 화면의 `maxLength`만으로는 부족하다(개발자 도구로 우회). 서버가 공백을 뺀 길이(1~200자)와 간격(아바타마다 마지막 메시지 시각, 1초)을 검사한다. 거절은 개인 큐로 보낸 사람에게만 알려, 다른 사람 화면에는 아무 일도 없다.
- 1:1 채팅도 서버가 "두 사람"만 받게 보낸다(`pairOf`). 관전자는 받지 못한다(테스트로 확인).

**6. 키 입력의 남은 상태 (실제로 찾은 버그)**
- Phaser는 F를 "방금 눌림(JustDown)"으로 기억해 두었다가 다음 확인 때 알려 준다. 채팅 입력칸이나 에디터에서 `f`를 치는 동안 고양이는 멈춰 있지만 그 기억은 남아, 입력칸을 벗어나 책상 앞에 서는 순간 F를 누르지 않았는데 앉아 버릴 수 있었다. 걷지 않는 프레임마다 `JustDown`을 한 번 불러 기억을 비운다.

#### 검증
- `LoungeWebSocketTest` (모두 16개, 유예는 테스트에서 2초): 수락 전 상대 변경 버림 → 요청·수락 → 상대 변경이 주인·관전자에게, 1:1 채팅은 두 사람만, 끝낸 뒤 변경 버림 / 페어는 회원끼리만(게스트 요청, 게스트 주인 방 요청 무시) / 주인 본인이 다른 탭으로 관전하면 `pairable=false`, 게스트 관전자는 `true`(로그인하면 되므로) / 회원 주인 재연결 때 같은 방 id·관전자 reset·자리 비운 동안 상대 쓰기 버림 / 상대 재연결 때 페어 이어짐 / 유예가 지나면 페어 끝·방 닫힘 / 전체 채팅 방송·공백 정리, 1초 안 연속·201자 거절, 나중에 들어온 사람이 최근 채팅을 받음
- 브라우저 탭 두 개 (2026-11-12): 페어 요청·수락, 동시 편집, 커서 이름표, 1:1 채팅 / 전체 채팅이 다른 탭의 패널 채팅 탭과 머리 위 말풍선에 나타남, 1초 안 연속은 "조금 천천히 보내 주세요" / 주인 아바타 id 로 들어가면 주소가 `/spectate/d-…`로 바뀜, 주인 새로고침 때 관전 화면에 "주인 연결이 잠깐 끊겼어요" 뒤 같은 방으로 코드가 돌아오고 주인이 친 글자가 다시 전달됨

#### 확인하지 못한 것
- 페어 상대 재연결은 브라우저로 확인하지 못했다. 브라우저 탭들이 같은 계정이라 같은 회원끼리는 페어가 안 되기 때문이다(통합 테스트로만 확인).
- 지도를 접었을 때 띠의 페어 요청 표시(깜빡이는 ⇄, 마우스를 올리면 "OO님 외 N명이 페어를 요청했어요")는 타입 검사·린트까지만 확인했다. 요청을 보낼 다른 회원 계정이 없어 브라우저로 보지 못했다.
