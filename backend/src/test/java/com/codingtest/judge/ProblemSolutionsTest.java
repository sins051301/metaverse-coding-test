package com.codingtest.judge;

import com.codingtest.TestcontainersConfiguration;
import com.codingtest.problem.Problem;
import com.codingtest.problem.ProblemRepository;
import com.codingtest.problem.TestCase;
import com.codingtest.problem.TestCaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * src/test/resources/problems/{id}-{slug}/ 의 검증용 코드로 문제 데이터와 판정기를 함께 검증한다.
 * 파일 이름이 기대 판정을 뜻한다: accepted = AC(100점), wrong = WA, slow = TLE.
 */
@Tag("docker")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProblemSolutionsTest {

	private static final Map<String, Verdict> EXPECTED_VERDICTS = Map.of(
			"accepted", Verdict.AC,
			"wrong", Verdict.WA,
			"slow", Verdict.TLE);

	@Autowired
	private Judge judge;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private TestCaseRepository testCaseRepository;

	private List<TestCase> testCases(long problemId) {
		return testCaseRepository.findByProblemIdOrderByHiddenAscSequenceAsc(problemId);
	}

	private JudgeResult judge(long problemId, Language language, String sourceCode, JudgeMode mode) {
		Problem problem = problemRepository.findById(problemId).orElseThrow();
		return judge.judge(problem, testCases(problemId), language, sourceCode, mode);
	}

	@TestFactory
	Stream<DynamicTest> solutionsGetExpectedVerdict() throws IOException {
		// Spring 경로 패턴은 {java,js} 같은 중괄호 묶음을 지원하지 않아 확장자는 직접 거른다.
		Resource[] solutions = Arrays.stream(new PathMatchingResourcePatternResolver().getResources("classpath*:problems/*/*"))
				.filter(resource -> resource.getFilename() != null
						&& (resource.getFilename().endsWith(".java") || resource.getFilename().endsWith(".js")))
				.toArray(Resource[]::new);
		assertThat(solutions).hasSize(22);
		return Arrays.stream(solutions).map(solution -> {
			String[] pathSegments = pathSegments(solution);
			String folderName = pathSegments[pathSegments.length - 2];
			String fileName = pathSegments[pathSegments.length - 1];
			long problemId = Long.parseLong(folderName.substring(0, folderName.indexOf('-')));
			Language language = fileName.endsWith(".java") ? Language.JAVA : Language.JAVASCRIPT;
			Verdict expectedVerdict = EXPECTED_VERDICTS.get(fileName.substring(0, fileName.indexOf('.')));
			return DynamicTest.dynamicTest(folderName + "/" + fileName + " -> " + expectedVerdict, () -> {
				JudgeResult result = judge(problemId, language, solution.getContentAsString(StandardCharsets.UTF_8), JudgeMode.SUBMIT);

				assertThat(result.verdict()).isEqualTo(expectedVerdict);
				if (expectedVerdict == Verdict.AC) {
					assertThat(result.score()).isEqualTo(100.0);
				}
			});
		});
	}

	private int exampleCount(long problemId) {
		return (int) testCases(problemId).stream().filter(testCase -> !testCase.isHidden()).count();
	}

	private static String[] pathSegments(Resource resource) {
		try {
			return resource.getURL().getPath().split("/");
		} catch (IOException exception) {
			throw new java.io.UncheckedIOException(exception);
		}
	}

	@Test
	@DisplayName("[코드 실행]은 예제만 돌리고 입력·기댓값·반환값·유저 출력을 보여준다")
	void runModeUsesExamplesOnly() {
		JudgeResult result = judge(1, Language.JAVASCRIPT, """
				function solution(numbers) {
				    console.log(numbers.length);
				    return numbers.filter((number) => number % 2 === 0).length;
				}
				""", JudgeMode.RUN);

		assertThat(result.tests()).hasSize(exampleCount(1)).allSatisfy(test -> {
			assertThat(test.hidden()).isFalse();
			assertThat(test.input()).isNotNull();
			assertThat(test.actual()).isEqualTo(test.expected());
		});
		assertThat(result.tests().getFirst().userOutput()).isEqualTo("5\n");
	}

	@Test
	@DisplayName("[제출]은 예제와 히든을 모두 돌리고, 히든 테스트는 판정·시간·메모리만 남기며 점수를 낸다")
	void submitModeHidesHiddenTestDetails() {
		JudgeResult result = judge(1, Language.JAVA, """
				import java.util.Arrays;
				class Solution {
				    int solution(int[] numbers) {
				        System.out.println(Arrays.toString(numbers));
				        return numbers.length > 5 ? -1 : (int) Arrays.stream(numbers).filter(number -> number % 2 == 0).count();
				    }
				}
				""", JudgeMode.SUBMIT);

		int exampleCount = exampleCount(1);
		assertThat(result.tests()).hasSize(testCases(1).size());
		assertThat(result.tests().subList(exampleCount, result.tests().size())).allSatisfy(test -> {
			assertThat(test.hidden()).isTrue();
			assertThat(test.input()).isNull();
			assertThat(test.expected()).isNull();
			assertThat(test.actual()).isNull();
			assertThat(test.userOutput()).isNull();
			assertThat(test.errorMessage()).isNull();
		});
		assertThat(result.verdict()).isEqualTo(Verdict.WA);
		assertThat(result.score()).isGreaterThan(0.0).isLessThan(100.0);
	}

	@Test
	@DisplayName("컴파일 에러는 테스트 없이 CE 와 컴파일 로그만 돌려준다")
	void compileErrorReturnsCompileLog() {
		JudgeResult result = judge(1, Language.JAVA,
				"class Solution { int solution(int[] numbers) { return } }", JudgeMode.SUBMIT);

		assertThat(result.verdict()).isEqualTo(Verdict.CE);
		assertThat(result.compileLog()).contains("Solution.java");
		assertThat(result.tests()).isEmpty();
		assertThat(result.score()).isZero();
	}

}
