package com.codingtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V1 마이그레이션의 UNIQUE·CHECK 제약이 README 8장 규칙대로 잘못된 행을 막는지 확인한다.
 */
@Tag("docker")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SchemaConstraintsTest {

	private static final String INSERT_SUBMISSION = """
			INSERT INTO submissions (user_id, problem_id, language, source_code, status, verdict, score, compile_log, created_at, judged_at)
			VALUES (?, 1, ?, 'code', ?, ?, ?, ?, NOW(), ?)""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long userId;

	@BeforeEach
	void insertUser() {
		jdbcTemplate.update("INSERT INTO users (google_subject, email, nickname, created_at) VALUES ('subject', 'user@example.com', 'nickname', NOW())");
		userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	@Test
	@DisplayName("같은 문제에 같은 이름의 파라미터, 지원하지 않는 타입은 들어가지 않는다")
	void rejectsInvalidParameters() {
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO problem_parameters (problem_id, position, name, type) VALUES (1, 2, 'numbers', 'int')"))
				.hasMessageContaining("uk_problem_parameters_name");
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO problem_parameters (problem_id, position, name, type) VALUES (1, 2, 'flag', 'char')"))
				.hasMessageContaining("ck_problem_parameters_type");
	}

	@Test
	@DisplayName("닉네임은 중복될 수 없다")
	void rejectsDuplicateNickname() {
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO users (google_subject, email, nickname, created_at) VALUES ('other', 'other@example.com', 'nickname', NOW())"))
				.hasMessageContaining("uk_users_nickname");
	}

	@Test
	@DisplayName("채점이 끝난 제출만 판정·점수·완료 시각을 가진다")
	void requiresResultOnlyWhenDone() {
		assertThatCode(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "PENDING", null, null, null, null)).doesNotThrowAnyException();
		assertThatCode(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "DONE", "AC", 100.0, null, "2026-11-04 00:00:00")).doesNotThrowAnyException();

		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "DONE", null, null, null, null))
				.hasMessageContaining("ck_submissions_done_result");
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "PENDING", "AC", 100.0, null, null))
				.hasMessageContaining("ck_submissions_done_result");
	}

	@Test
	@DisplayName("컴파일 로그는 CE 일 때만, CE 면 반드시 있다")
	void requiresCompileLogOnlyForCompileError() {
		assertThatCode(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "DONE", "CE", 0.0, "error", "2026-11-04 00:00:00")).doesNotThrowAnyException();

		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "DONE", "CE", 0.0, null, "2026-11-04 00:00:00"))
				.hasMessageContaining("ck_submissions_compile_log");
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "DONE", "AC", 100.0, "warning", "2026-11-04 00:00:00"))
				.hasMessageContaining("ck_submissions_compile_log");
	}

	@Test
	@DisplayName("언어·상태·판정은 정해진 값만, 점수는 0~100만 들어간다")
	void rejectsUnknownCodesAndOutOfRangeScore() {
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "PYTHON", "PENDING", null, null, null, null))
				.hasMessageContaining("ck_submissions_language");
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "QUEUED", null, null, null, null))
				.hasMessageContaining("ck_submissions_status");
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "DONE", "OK", 100.0, null, "2026-11-04 00:00:00"))
				.hasMessageContaining("ck_submissions_verdict");
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT_SUBMISSION, userId, "JAVA", "DONE", "WA", 100.5, null, "2026-11-04 00:00:00"))
				.hasMessageContaining("ck_submissions_score");
	}

}
