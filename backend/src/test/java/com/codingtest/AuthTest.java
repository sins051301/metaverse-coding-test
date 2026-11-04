package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.user.User;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Google 로그인 화면은 테스트에서 거칠 수 없으므로, 로그인 직후 단계(회원 찾기·만들기 → 우리 JWT 발급)부터 검증한다.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthTest {

	private static final String RUN_REQUEST = """
			{"language": "JAVASCRIPT", "sourceCode": "function solution(numbers) { return 0; }"}""";

	@Autowired
	private MockMvcTester mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("처음 로그인하면 사용자_XXXXXX 닉네임으로 회원을 만들고, 다시 로그인하면 같은 회원을 돌려준다")
	void createsUserOnFirstLoginOnly() {
		User first = userService.findOrCreate("google-subject-first-login", "first@example.com");
		User again = userService.findOrCreate("google-subject-first-login", "first@example.com");
		User other = userService.findOrCreate("google-subject-other", "other@example.com");

		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(first.getNickname()).matches("사용자_[A-Z2-9]{6}");
		assertThat(other.getNickname()).isNotEqualTo(first.getNickname());
	}

	@Test
	@DisplayName("가입 시각은 JVM 시간대와 상관없이 UTC 로 저장된다")
	void storesCreatedAtInUtc() {
		User user = userService.findOrCreate("google-subject-utc", "utc@example.com");

		Long secondsFromUtcNow = jdbcTemplate.queryForObject(
				"SELECT ABS(TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP())) FROM users WHERE id = ?", Long.class, user.getId());
		assertThat(secondsFromUtcNow).isLessThan(60);
	}

	@Test
	@DisplayName("발급한 토큰으로 내 정보를 조회한다")
	void returnsCurrentUserForValidToken() {
		User user = userService.findOrCreate("google-subject-me", "me@example.com");

		assertThat(mockMvc.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user.getId())))
				.hasStatusOk().bodyJson()
				.hasPathSatisfying("$.id", id -> assertThat(id).asNumber().isEqualTo(user.getId().intValue()))
				.hasPathSatisfying("$.nickname", nickname -> assertThat(nickname).isEqualTo(user.getNickname()));
	}

	@Test
	@DisplayName("토큰이 없거나 위조되면 로그인 화면으로 보내지 않고 401")
	void rejectsMissingOrForgedToken() {
		assertThat(mockMvc.get().uri("/api/users/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mockMvc.post().uri("/api/problems/1/runs").contentType(MediaType.APPLICATION_JSON).content(RUN_REQUEST))
				.hasStatus(HttpStatus.UNAUTHORIZED);

		String token = tokenService.issue(userService.findOrCreate("google-subject-forged", "forged@example.com").getId());
		String forged = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
		assertThat(mockMvc.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("문제 조회와 Swagger 는 로그인 없이 열리고, API 문서에 Bearer 인증 방식이 들어 있다")
	void keepsPublicPagesOpen() {
		assertThat(mockMvc.get().uri("/api/problems")).hasStatusOk();
		assertThat(mockMvc.get().uri("/api/problems/1")).hasStatusOk();
		assertThat(mockMvc.get().uri("/v3/api-docs")).hasStatusOk().bodyJson()
				.hasPathSatisfying("$.components.securitySchemes.bearer.scheme", scheme -> assertThat(scheme).isEqualTo("bearer"));
	}

	@Test
	@DisplayName("Google 로그인 시작 주소는 Google 인증 화면으로 보낸다")
	void startsGoogleLogin() {
		MvcTestResult result = mockMvc.get().uri("/oauth2/authorization/google").exchange();

		assertThat(result).hasStatus(HttpStatus.FOUND);
		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).startsWith("https://accounts.google.com/");
	}

}
