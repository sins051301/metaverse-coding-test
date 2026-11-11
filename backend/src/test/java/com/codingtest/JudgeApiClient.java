package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.user.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 테스트에서 "로그인한 유저로 제출(또는 실행)하고, 채점이 끝날 때까지 기다려 결과를 받는" 과정을 한 줄로 쓰게 해 주는 도우미.
 *
 * <p>실제 Google 로그인은 테스트에서 거칠 수 없으므로, 회원을 직접 만들고 서버의 TokenService 로 JWT 를 발급해 쓴다.
 */
public class JudgeApiClient {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	// Java 제출은 javac 때문에 5초 안팎이 걸린다. 여러 건이 동시에 돌면 더 늘어나므로 넉넉히 잡는다.
	private static final Duration JUDGE_TIMEOUT = Duration.ofSeconds(120);

	private final MockMvcTester mockMvc;
	private final String accessToken;

	public JudgeApiClient(MockMvcTester mockMvc, UserService userService, TokenService tokenService, String googleSubject) {
		this.mockMvc = mockMvc;
		this.accessToken = tokenService.issue(userService.findOrCreate(googleSubject, googleSubject + "@example.com").getId());
	}

	public String authorization() {
		return "Bearer " + accessToken;
	}

	/**
	 * POST 로 접수만 한다. 응답은 202 와 {"id", "status": "PENDING"} 이어야 한다.
	 *
	 * @param kind "submissions" 또는 "runs"
	 * @return 접수된 제출·실행 id
	 */
	public long accept(String kind, long problemId, String language, String sourceCode) {
		MvcTestResult result = post(kind, problemId, language, sourceCode);
		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		JsonNode body = read(result);
		assertThat(body.path("status").asString()).isEqualTo("PENDING");
		return body.path("id").asLong();
	}

	public MvcTestResult post(String kind, long problemId, String language, String sourceCode) {
		return mockMvc.post().uri("/api/problems/{problemId}/{kind}", problemId, kind)
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(JSON_MAPPER.writeValueAsString(Map.of("language", language, "sourceCode", sourceCode)))
				.exchange();
	}

	/**
	 * 조회 API 를 반복해서 부르다가 status 가 DONE 이 되면 그 응답을 돌려준다.
	 *
	 * <p>Awaitility: await().atMost(최대 시간).pollInterval(확인 간격).until(조건)
	 * = "조건이 참이 될 때까지 확인 간격마다 다시 확인하고, 최대 시간이 지나면 테스트 실패".
	 * 실패하면 마지막으로 본 상태를 메시지에 담아 준다.
	 *
	 * @param kind "submissions" 또는 "runs"
	 */
	public JsonNode awaitDone(String kind, long id) {
		AtomicReference<JsonNode> latest = new AtomicReference<>();
		await().atMost(JUDGE_TIMEOUT).pollInterval(Duration.ofMillis(300)).until(() -> {
			latest.set(get(kind, id));
			return "DONE".equals(latest.get().path("status").asString());
		});
		return latest.get();
	}

	public JsonNode get(String kind, long id) {
		MvcTestResult result = mockMvc.get().uri("/api/{kind}/{id}", kind, id)
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.exchange();
		assertThat(result).hasStatusOk();
		return read(result);
	}

	public MvcTestResult getRaw(String kind, long id) {
		return mockMvc.get().uri("/api/{kind}/{id}", kind, id)
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.exchange();
	}

	public JsonNode me() {
		return read(mockMvc.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, authorization()).exchange());
	}

	private static JsonNode read(MvcTestResult result) {
		try {
			return JSON_MAPPER.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		} catch (UnsupportedEncodingException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
