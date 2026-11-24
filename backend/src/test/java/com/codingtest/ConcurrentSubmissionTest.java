package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여러 유저가 거의 동시에 제출할 때, 모두 정상 판정되는지와 시간이 얼마나 늘어나는지 본다.
 *
 * <p>6단계(제한 없음)에서는 제출 수만큼 채점 컨테이너가 한꺼번에 떴다 (컨테이너 하나 최대 265MB). 그때 결과가 기준값이고,
 * 7단계에서 동시 채점 수 제한(judge.concurrency)을 넣은 뒤 같은 테스트로 비교한다 (README 7장).
 * 동시 채점 수를 바꿔 재려면: ./gradlew concurrencyTest -PjudgeConcurrency=2
 * 무거워서 judgeTest 에서는 빠지고 ./gradlew concurrencyTest 로 따로 돌린다.
 */
@Tag("concurrency")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConcurrentSubmissionTest {

	// 노트북(Docker VM 7.5GB)에서 메모리 부족으로 강제 종료되지 않을 만큼만 띄운다. 265MB × 10 = 약 2.7GB.
	private static final int SUBMISSION_COUNT = 10;

	private static final String JAVA_ACCEPTED = """
			class Solution {
			    int solution(int[] numbers) {
			        int count = 0;
			        for (int number : numbers) {
			            if (number % 2 == 0) count++;
			        }
			        return count;
			    }
			}
			""";
	private static final String JAVASCRIPT_ACCEPTED = "function solution(numbers) { return numbers.filter((number) => number % 2 === 0).length; }";

	@Autowired
	private MockMvcTester mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private TokenService tokenService;

	@Value("${judge.concurrency}")
	private int judgeConcurrency;

	@Value("${judge.virtual-threads}")
	private boolean judgeVirtualThreads;

	@Test
	@DisplayName("제출 10건(Java 5, JavaScript 5)을 동시에 보내면 모두 AC 로 끝난다 (걸린 시간을 출력)")
	void judgesConcurrentSubmissions() throws Exception {
		List<JudgeApiClient> users = IntStream.range(0, SUBMISSION_COUNT)
				.mapToObj(index -> new JudgeApiClient(mockMvc, userService, tokenService, "concurrency-test-" + index))
				.toList();

		JvmThreadMetrics.Snapshot baseline = JvmThreadMetrics.snapshotAfterGc();
		JvmThreadMetrics.PeakSampler sampler = new JvmThreadMetrics.PeakSampler();
		Instant start = Instant.now();
		List<JsonNode> results = new ArrayList<>();
		// 가상 스레드로 유저 10명이 동시에 "제출 → 결과 기다리기"를 한다.
		try (sampler; ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<JsonNode>> futures = new ArrayList<>();
			for (int index = 0; index < SUBMISSION_COUNT; index++) {
				JudgeApiClient user = users.get(index);
				boolean java = index % 2 == 0;
				futures.add(clients.submit(() -> user.awaitDone("submissions",
						user.accept("submissions", 1, java ? "JAVA" : "JAVASCRIPT", java ? JAVA_ACCEPTED : JAVASCRIPT_ACCEPTED))));
			}
			for (Future<JsonNode> future : futures) {
				results.add(future.get());
			}
		}
		Duration total = Duration.between(start, Instant.now());

		assertThat(results).allSatisfy(result -> assertThat(result.path("verdict").asString()).isEqualTo("AC"));

		List<Long> judgeMillis = results.stream()
				.map(result -> Duration.between(Instant.parse(result.path("createdAt").asString()), Instant.parse(result.path("judgedAt").asString())).toMillis())
				.sorted()
				.toList();
		System.out.printf("[동시 제출 %d건, 동시 채점 수 %d] 전체 %.1f초, 건별(접수→채점 완료) 최소 %.1f초 / 중간 %.1f초 / 최대 %.1f초%n",
				SUBMISSION_COUNT, judgeConcurrency, total.toMillis() / 1000.0,
				judgeMillis.getFirst() / 1000.0, judgeMillis.get(judgeMillis.size() / 2) / 1000.0, judgeMillis.getLast() / 1000.0);
		System.out.printf("[채점 작업 스레드: %s] 측정 중 최댓값 - 시작 전: %s%n",
				judgeVirtualThreads ? "가상 스레드" : "플랫폼 스레드", sampler.peak().minus(baseline).describe());
	}

}
