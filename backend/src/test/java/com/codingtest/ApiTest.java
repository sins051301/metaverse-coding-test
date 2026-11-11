package com.codingtest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문제 조회 API. 실행·제출 API 는 JudgeApiTest.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiTest {

	@Autowired
	private MockMvcTester mockMvc;

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
	}

}
