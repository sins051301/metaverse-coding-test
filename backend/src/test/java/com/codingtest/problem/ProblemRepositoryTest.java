package com.codingtest.problem;

import com.codingtest.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway seed(V2)로 들어간 문제 데이터를 검사한다. DB가 막지 못하는 규칙(README 8장)도 여기서 지킨다.
 */
@Tag("docker")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProblemRepositoryTest {

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private TestCaseRepository testCaseRepository;

	private List<TestCase> testCases(Problem problem) {
		return testCaseRepository.findByProblemIdOrderByHiddenAscSequenceAsc(problem.getId());
	}

	@Test
	@DisplayName("seed 문제 5개를 id 순서로 읽고, 지문·예제·히든 테스트가 모두 있다")
	void loadsAllSeedProblemsInIdOrder() {
		assertThat(problemRepository.findAll()).extracting(Problem::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
		assertThat(problemRepository.findAll()).allSatisfy(problem -> {
			assertThat(problem.getTitle()).isNotBlank();
			assertThat(problem.getDescription()).contains("## 문제 설명");
			assertThat(testCases(problem)).anySatisfy(testCase -> assertThat(testCase.isHidden()).isFalse());
			assertThat(testCases(problem)).anySatisfy(testCase -> assertThat(testCase.isHidden()).isTrue());
		});
	}

	@Test
	@DisplayName("테스트 케이스는 예제가 먼저, 그 안에서는 sequence 순서로 온다")
	void ordersExamplesBeforeHiddenTests() {
		assertThat(problemRepository.findAll()).allSatisfy(problem ->
				assertThat(testCases(problem)).isSortedAccordingTo((first, second) -> first.isHidden() != second.isHidden()
						? Boolean.compare(first.isHidden(), second.isHidden())
						: Integer.compare(first.getSequence(), second.getSequence())));
	}

	@Test
	@DisplayName("파라미터 position 은 1부터 빈틈없이 이어지고, 모든 테스트 입력의 인자 개수가 파라미터 개수와 같다")
	void everyTestInputMatchesTheSignature() {
		assertThat(problemRepository.findAll()).allSatisfy(problem -> {
			assertThat(problem.getParameters()).extracting(ProblemParameter::getPosition)
					.containsExactlyElementsOf(IntStream.rangeClosed(1, problem.getParameters().size()).boxed().toList());
			assertThat(testCases(problem)).allSatisfy(testCase -> {
				assertThat(testCase.getInput().isArray()).isTrue();
				assertThat(testCase.getInput().size()).isEqualTo(problem.getParameters().size());
			});
		});
	}

	@Test
	@DisplayName("제한을 지정한 문제는 그 값을, 아니면 DB 기본값(3초, 265MB)을 쓴다")
	void appliesProblemLimitOrDefault() {
		assertThat(problemRepository.findById(1L).orElseThrow().executionLimits().timeLimit()).isEqualTo(Duration.ofSeconds(3));
		assertThat(problemRepository.findById(5L).orElseThrow().executionLimits().timeLimit()).isEqualTo(Duration.ofSeconds(2));
		assertThat(problemRepository.findById(5L).orElseThrow().executionLimits().memoryLimitMb()).isEqualTo(265);
	}

}
