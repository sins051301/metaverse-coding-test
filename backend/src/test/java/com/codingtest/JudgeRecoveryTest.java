package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.judge.JudgeRecovery;
import com.codingtest.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "채점 도중 서버가 꺼졌다"를 흉내 낸다: DB 에 PENDING·JUDGING 행을 직접 넣어 두고(작업 스레드에는 아무것도 넘기지 않음),
 * 서버 시작 때 도는 복구(JudgeRecovery#resumeUnfinished)를 불러 그 행들이 끝까지 채점되는지 본다.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JudgeRecoveryTest {

	private static final String SOURCE_CODE = "function solution(numbers) { return numbers.filter((number) => number % 2 === 0).length; }";

	@Autowired
	private MockMvcTester mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JudgeRecovery judgeRecovery;

	@Test
	@DisplayName("서버가 다시 켜지면 남아 있던 PENDING·JUDGING 제출과 실행을 다시 채점해 DONE 으로 만든다")
	void resumesUnfinishedJudging() {
		JudgeApiClient user = new JudgeApiClient(mockMvc, userService, tokenService, "recovery-test");
		long userId = user.me().path("id").asLong();
		long pendingSubmissionId = insert("submissions", userId, "PENDING");
		long judgingSubmissionId = insert("submissions", userId, "JUDGING");
		long judgingRunId = insert("runs", userId, "JUDGING");

		judgeRecovery.resumeUnfinished();

		assertThat(user.awaitDone("submissions", pendingSubmissionId).path("verdict").asString()).isEqualTo("AC");
		assertThat(user.awaitDone("submissions", judgingSubmissionId).path("verdict").asString()).isEqualTo("AC");
		assertThat(user.awaitDone("runs", judgingRunId).path("verdict").asString()).isEqualTo("AC");
	}

	private long insert(String table, long userId, String status) {
		jdbcTemplate.update("INSERT INTO " + table + " (user_id, problem_id, language, source_code, status, created_at) "
				+ "VALUES (?, 1, 'JAVASCRIPT', ?, ?, UTC_TIMESTAMP())", userId, SOURCE_CODE, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

}
