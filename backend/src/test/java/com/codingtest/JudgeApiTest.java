package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.judge.Language;
import com.codingtest.problem.ProblemService;
import com.codingtest.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [코드 실행]과 [제출]: 접수(202) → 작업 스레드가 채점 → 조회 API 로 결과 확인.
 * 테스트마다 새 회원을 만들어 서로의 기록(푼 문제 등)이 섞이지 않게 한다.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JudgeApiTest {

	private static final String ACCEPTED_JAVASCRIPT = "function solution(numbers) { return numbers.filter((number) => number % 2 === 0).length; }";
	private static final String WRONG_JAVASCRIPT = "function solution(numbers) { return numbers.length; }";
	private static final String ACCEPTED_JAVA = "class Solution { int solution(int[] numbers) { int count = 0; for (int number : numbers) if (number % 2 == 0) count++; return count; } }";

	@Autowired
	private MockMvcTester mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private ProblemService problemService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private JudgeApiClient newUser() {
		return new JudgeApiClient(mockMvc, userService, tokenService, "judge-api-test-" + UUID.randomUUID());
	}

	@Test
	@DisplayName("[코드 실행]은 202 로 접수하고, 채점이 끝나면 예제만의 결과(입력·기댓값·반환값)를 저장해 보여 준다")
	void runsExamplesAsynchronously() {
		JudgeApiClient user = newUser();
		long runId = user.accept("runs", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT);

		JsonNode run = user.awaitDone("runs", runId);

		assertThat(run.path("verdict").asString()).isEqualTo("AC");
		assertThat(run.path("passedCount").asInt()).isEqualTo(3);
		assertThat(run.path("tests")).hasSize(3);
		assertThat(run.path("tests").get(0).path("hidden").asBoolean()).isFalse();
		assertThat(run.path("tests").get(0).path("input").toString()).isEqualTo("[[1,2,3,4,5]]");
		assertThat(run.path("tests").get(0).path("actual").asInt()).isEqualTo(2);
		assertThat(user.me().path("totalSolved").asLong()).as("실행은 푼 문제에 반영되지 않는다").isZero();
	}

	@Test
	@DisplayName("[제출]이 AC 면 100점, 히든 테스트는 판정·시간·메모리만 보이고, 푼 문제가 1 늘어난다")
	void acceptedSubmissionCountsAsSolved() {
		JudgeApiClient user = newUser();
		long submissionId = user.accept("submissions", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT);

		JsonNode submission = user.awaitDone("submissions", submissionId);

		assertThat(submission.path("verdict").asString()).isEqualTo("AC");
		assertThat(submission.path("score").asDouble()).isEqualTo(100.0);
		assertThat(submission.path("tests")).hasSize(10);
		JsonNode firstHidden = submission.path("tests").get(3);
		assertThat(firstHidden.path("hidden").asBoolean()).isTrue();
		assertThat(firstHidden.path("input").isNull()).isTrue();
		assertThat(firstHidden.path("actual").isNull()).isTrue();
		assertThat(user.me().path("totalSolved").asLong()).isEqualTo(1);

		long againId = user.accept("submissions", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT);
		user.awaitDone("submissions", againId);
		assertThat(user.me().path("totalSolved").asLong()).as("같은 문제를 다시 맞혀도 한 번만 센다").isEqualTo(1);
	}

	@Test
	@DisplayName("[제출]이 오답이면 WA 와 부분 점수, 푼 문제에는 반영되지 않는다")
	void wrongSubmissionIsNotSolved() {
		JudgeApiClient user = newUser();
		JsonNode submission = user.awaitDone("submissions", user.accept("submissions", 1, "JAVASCRIPT", WRONG_JAVASCRIPT));

		assertThat(submission.path("verdict").asString()).isEqualTo("WA");
		assertThat(submission.path("score").asDouble()).isGreaterThanOrEqualTo(0.0).isLessThan(100.0);
		assertThat(user.me().path("totalSolved").asLong()).isZero();
	}

	@Test
	@DisplayName("컴파일 에러는 DONE + CE, 컴파일 로그만 있고 테스트 결과는 없다")
	void compileErrorIsStoredWithLog() {
		JudgeApiClient user = newUser();
		JsonNode submission = user.awaitDone("submissions",
				user.accept("submissions", 1, "JAVA", "class Solution { int solution(int[] numbers) { return } }"));

		assertThat(submission.path("verdict").asString()).isEqualTo("CE");
		assertThat(submission.path("compileLog").asString()).contains("Solution.java");
		assertThat(submission.path("tests")).isEmpty();
	}

	@Test
	@DisplayName("남의 제출·실행은 404 (있다는 사실도 드러내지 않음), 토큰이 없으면 401")
	void hidesOtherUsersRecords() {
		JudgeApiClient owner = newUser();
		JudgeApiClient other = newUser();
		long submissionId = owner.accept("submissions", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT);
		long runId = owner.accept("runs", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT);

		assertThat(other.getRaw("submissions", submissionId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(other.getRaw("runs", runId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mockMvc.get().uri("/api/submissions/{id}", submissionId)).hasStatus(HttpStatus.UNAUTHORIZED);

		// 뒤에서 도는 채점이 다음 테스트와 겹치지 않도록 끝날 때까지 기다린다.
		owner.awaitDone("submissions", submissionId);
		owner.awaitDone("runs", runId);
	}

	@Test
	@DisplayName("없는 문제는 404, 빈 코드·지원하지 않는 언어는 접수하지 않고 400")
	void rejectsInvalidRequests() {
		JudgeApiClient user = newUser();

		assertThat(user.post("submissions", 999, "JAVA", "class Solution {}")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(user.post("runs", 1, "JAVA", " ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(user.post("runs", 1, "PYTHON", "def solution(numbers): return 0")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("끝나지 않은 제출·실행이 이미 2건이면 3번째는 429 로 거절하고, 앞의 것이 끝나면 다시 받는다")
	void limitsUnfinishedJudgingPerUser() {
		JudgeApiClient user = newUser();
		// Java 는 javac 때문에 5초 안팎이 걸려서, 아래 세 번째 요청을 보낼 때까지 앞의 두 건이 끝나지 않는다.
		long submissionId = user.accept("submissions", 1, "JAVA", ACCEPTED_JAVA);
		long runId = user.accept("runs", 1, "JAVA", ACCEPTED_JAVA);

		assertThat(user.post("submissions", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);

		user.awaitDone("submissions", submissionId);
		user.awaitDone("runs", runId);
		user.awaitDone("runs", user.accept("runs", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT));
	}

	@Test
	@DisplayName("전체 대기가 상한(50건)만큼 쌓이면 처음 제출하는 유저도 429 로 거절한다")
	void rejectsWhenQueueIsFull() {
		JudgeApiClient filler = newUser();
		long fillerId = filler.me().path("id").asLong();
		// 채점 이벤트 없이 DB 에 PENDING 만 넣는다. 작업자가 집어 가지 않으므로 끝나지 않은 채로 남는다.
		for (int index = 0; index < 50; index++) {
			jdbcTemplate.update("INSERT INTO runs (user_id, problem_id, language, source_code, status, created_at) "
					+ "VALUES (?, 1, 'JAVASCRIPT', ?, 'PENDING', UTC_TIMESTAMP())", fillerId, ACCEPTED_JAVASCRIPT);
		}
		try {
			assertThat(newUser().post("submissions", 1, "JAVASCRIPT", ACCEPTED_JAVASCRIPT)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		} finally {
			jdbcTemplate.update("DELETE FROM runs WHERE user_id = ?", fillerId);
		}
	}

	@TestFactory
	@DisplayName("모든 문제의 시작 코드는 그대로 실행해도 컴파일·실행 에러 없이 채점된다")
	Stream<DynamicTest> starterCodesRun() {
		JudgeApiClient user = newUser();
		return problemService.findAll().stream().flatMap(problem -> Stream.of(Language.values()).map(language ->
				DynamicTest.dynamicTest(problem.id() + " " + language, () -> {
					String starterCode = problemService.getDetail(problem.id()).starterCode().get(language);
					JsonNode run = user.awaitDone("runs", user.accept("runs", problem.id(), language.name(), starterCode));
					assertThat(run.path("verdict").asString()).isIn("AC", "WA");
				})));
	}

}
