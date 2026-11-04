package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.judge.Language;
import com.codingtest.problem.ProblemService;
import com.codingtest.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiTest {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mockMvc;

	@Autowired
	private ProblemService problemService;

	@Autowired
	private UserService userService;

	@Autowired
	private TokenService tokenService;

	private String accessToken;

	@BeforeEach
	void logIn() {
		accessToken = tokenService.issue(userService.findOrCreate("api-test-subject", "api-test@example.com").getId());
	}

	private MockMvcTester.MockMvcRequestBuilder run(long problemId, String language, String sourceCode) {
		return mockMvc.post().uri("/api/problems/{problemId}/runs", problemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(JSON_MAPPER.writeValueAsString(Map.of("language", language, "sourceCode", sourceCode)));
	}

	@Test
	@DisplayName("문제 목록은 id 순서로 제목과 난이도를 준다")
	void listsProblems() {
		assertThat(mockMvc.get().uri("/api/problems")).hasStatusOk()
				.bodyJson().extractingPath("$[*].id").asArray().containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	@DisplayName("문제 상세는 지문, 파라미터, 제한, 언어별 시작 코드를 준다")
	void returnsProblemDetail() {
		assertThat(mockMvc.get().uri("/api/problems/1")).hasStatusOk().bodyJson()
				.hasPathSatisfying("$.parameters[0].name", name -> assertThat(name).isEqualTo("numbers"))
				.hasPathSatisfying("$.timeLimitMs", timeLimit -> assertThat(timeLimit).isEqualTo(3000))
				.hasPathSatisfying("$.starterCode.JAVA", code -> assertThat(code).asString().contains("public int solution(int[] numbers)"))
				.hasPathSatisfying("$.starterCode.JAVASCRIPT", code -> assertThat(code).asString().contains("function solution(numbers)"));
	}

	@Test
	@DisplayName("없는 문제는 404 를 표준 에러 형식(ProblemDetail)으로 준다")
	void returnsNotFoundForUnknownProblem() {
		assertThat(mockMvc.get().uri("/api/problems/999")).hasStatus(HttpStatus.NOT_FOUND)
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(run(999, "JAVA", "class Solution {}")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("빈 코드, 지원하지 않는 언어는 채점하지 않고 400")
	void rejectsInvalidRunRequest() {
		assertThat(run(1, "JAVA", " ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(run(1, "PYTHON", "def solution(numbers): return 0")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("코드 실행은 예제만 채점해 판정과 테스트별 결과를 준다")
	void runsExamples() {
		assertThat(run(1, "JAVASCRIPT", "function solution(numbers) { return numbers.filter((number) => number % 2 === 0).length; }"))
				.hasStatusOk().bodyJson()
				.hasPathSatisfying("$.verdict", verdict -> assertThat(verdict).isEqualTo("AC"))
				.hasPathSatisfying("$.totalCount", total -> assertThat(total).isEqualTo(3))
				.hasPathSatisfying("$.tests[0].expected", expected -> assertThat(expected).isEqualTo(2));
	}

	@TestFactory
	@DisplayName("모든 문제의 시작 코드는 그대로 실행해도 컴파일·실행 에러 없이 채점된다")
	Stream<DynamicTest> starterCodesRun() {
		return problemService.findAll().stream().flatMap(problem -> Stream.of(Language.values()).map(language ->
				DynamicTest.dynamicTest(problem.id() + " " + language, () -> {
					String starterCode = problemService.getDetail(problem.id()).starterCode().get(language);
					assertThat(run(problem.id(), language.name(), starterCode)).hasStatusOk().bodyJson()
							.hasPathSatisfying("$.verdict", verdict -> assertThat(verdict).asString().isIn("AC", "WA"));
				})));
	}

}
