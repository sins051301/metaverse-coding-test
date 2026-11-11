package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제출 내역 API. 채점은 이 테스트의 관심사가 아니라서 제출 행을 DB 에 바로 넣는다.
 * 상태는 FAILED 로 넣는다: 결과 컬럼이 비어 있어도 CHECK 제약을 통과하고, 끝난 상태라 접수 제한(JudgeAdmission)에도 세지지 않는다.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SubmissionHistoryTest {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("이 문제에 낸 내 제출만 최신순으로, 소스 코드 없이 나오고 size 단위로 나뉜다")
	void listsOwnSubmissionsNewestFirst() {
		JudgeApiClient user = newUser();
		long userId = user.me().path("id").asLong();
		long oldest = insertSubmission(userId, 1);
		long middle = insertSubmission(userId, 1);
		long newest = insertSubmission(userId, 1);
		insertSubmission(userId, 2);
		insertSubmission(newUser().me().path("id").asLong(), 1);

		JsonNode firstPage = history(user, 1, Map.of("size", "2"));
		assertThat(ids(firstPage)).containsExactly(newest, middle);
		assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
		assertThat(firstPage.path("totalCount").asLong()).isEqualTo(3);
		assertThat(firstPage.path("submissions").get(0).has("sourceCode")).isFalse();

		JsonNode secondPage = history(user, 1, Map.of("size", "2", "page", "1"));
		assertThat(ids(secondPage)).containsExactly(oldest);
		assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
		assertThat(secondPage.path("totalCount").isNull()).isTrue();
	}

	@Test
	@DisplayName("제출 단건 조회에는 그때 낸 소스 코드가 들어 있다 (내역에서 행을 펼칠 때 쓴다)")
	void returnsSourceCodeForSingleSubmission() throws Exception {
		JudgeApiClient user = newUser();
		long submissionId = insertSubmission(user.me().path("id").asLong(), 1);

		MvcTestResult result = mockMvc.get().uri("/api/submissions/{id}", submissionId)
				.header(HttpHeaders.AUTHORIZATION, user.authorization()).exchange();

		assertThat(result).hasStatusOk();
		JsonNode submission = JSON_MAPPER.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertThat(submission.path("sourceCode").asString()).isEqualTo("function solution() {}");
	}

	@Test
	@DisplayName("전체 개수는 (user_id, problem_id) 인덱스만으로 센다 (EXPLAIN 에 Using index)")
	void countsWithIndexOnly() {
		Map<String, Object> plan = jdbcTemplate.queryForMap(
				"EXPLAIN SELECT COUNT(*) FROM submissions WHERE user_id = 1 AND problem_id = 1");

		assertThat(plan.get("key")).isEqualTo("ix_submissions_user_problem");
		assertThat(String.valueOf(plan.get("Extra"))).contains("Using index");
	}

	@Test
	@DisplayName("없는 문제는 404, size 가 범위(1~50)를 벗어나면 400, 토큰이 없으면 401")
	void rejectsInvalidRequests() {
		JudgeApiClient user = newUser();

		assertThat(get(user, 999, Map.of())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(user, 1, Map.of("size", "51"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(get(user, 1, Map.of("size", "0"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mockMvc.get().uri("/api/problems/1/submissions")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("목록 조회는 (user_id, problem_id) 인덱스만 읽고 따로 정렬하지 않는다 (EXPLAIN 에 Using filesort 가 없다)")
	void usesIndexOrderWithoutSorting() {
		Map<String, Object> plan = jdbcTemplate.queryForMap(
				"EXPLAIN SELECT id, language, status, verdict, score, created_at, judged_at FROM submissions "
						+ "WHERE user_id = 1 AND problem_id = 1 ORDER BY id DESC LIMIT 21");

		assertThat(plan.get("key")).isEqualTo("ix_submissions_user_problem");
		assertThat(String.valueOf(plan.get("Extra"))).doesNotContain("filesort");
	}

	private JudgeApiClient newUser() {
		return new JudgeApiClient(mockMvc, userService, tokenService, "history-test-" + UUID.randomUUID());
	}

	private long insertSubmission(long userId, long problemId) {
		jdbcTemplate.update("INSERT INTO submissions (user_id, problem_id, language, source_code, status, created_at) "
				+ "VALUES (?, ?, 'JAVASCRIPT', 'function solution() {}', 'FAILED', UTC_TIMESTAMP())", userId, problemId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private JsonNode history(JudgeApiClient user, long problemId, Map<String, String> parameters) {
		MvcTestResult result = get(user, problemId, parameters);
		assertThat(result).hasStatusOk();
		try {
			return JSON_MAPPER.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		} catch (UnsupportedEncodingException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private MvcTestResult get(JudgeApiClient user, long problemId, Map<String, String> parameters) {
		var request = mockMvc.get().uri("/api/problems/{problemId}/submissions", problemId)
				.header(HttpHeaders.AUTHORIZATION, user.authorization());
		parameters.forEach(request::param);
		return request.exchange();
	}

	private static List<Long> ids(JsonNode page) {
		return page.path("submissions").valueStream().map(submission -> submission.path("id").asLong()).toList();
	}

}
