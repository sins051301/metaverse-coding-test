package com.codingtest.judge;

import com.codingtest.problem.Problem;
import com.codingtest.problem.TestCase;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 문제의 테스트로 유저 코드를 실행하고, 테스트별 판정(AC/WA/TLE/MLE/RE/CE)과 점수를 낸다.
 */
@Component
public class Judge {

	private static final double FLOATING_POINT_TOLERANCE = 1e-6;
	private static final int TIMEOUT_KILL_EXIT_CODE = 137;

	private final SandboxRunner sandboxRunner;

	public Judge(SandboxRunner sandboxRunner) {
		this.sandboxRunner = sandboxRunner;
	}

	/**
	 * @param problemTestCases 문제의 전체 테스트 케이스, 예제가 먼저 오는 순서 (TestCaseRepository 가 이 순서로 돌려준다)
	 */
	public JudgeResult judge(Problem problem, List<TestCase> problemTestCases, Language language, String sourceCode, JudgeMode mode) {
		List<TestCase> testCases = problemTestCases.stream()
				.filter(testCase -> mode == JudgeMode.SUBMIT || !testCase.isHidden())
				.toList();
		List<String> inputs = testCases.stream().map(testCase -> testCase.getInput().toString()).toList();

		SandboxResult sandboxResult = sandboxRunner.run(language, sourceCode, inputs, problem.executionLimits());
		if (!sandboxResult.compiled()) {
			return new JudgeResult(Verdict.CE, sandboxResult.compileLog(), List.of());
		}

		List<TestJudgement> judgements = new ArrayList<>();
		for (int index = 0; index < testCases.size(); index++) {
			TestCase testCase = testCases.get(index);
			TestRunResult run = sandboxResult.testResults().get(index);
			Verdict verdict = classify(run, testCase.getExpected());
			judgements.add(testCase.isHidden()
					? new TestJudgement(index + 1, true, verdict, run.timeSeconds(), run.memoryKb(), null, null, null, null, null)
					: new TestJudgement(index + 1, false, verdict, run.timeSeconds(), run.memoryKb(),
							testCase.getInput(), testCase.getExpected(), run.returnValue(), run.userOutput(), run.stderr()));
		}
		Verdict overall = judgements.stream()
				.map(TestJudgement::verdict)
				.filter(verdict -> verdict != Verdict.AC)
				.findFirst()
				.orElse(Verdict.AC);
		return new JudgeResult(overall, sandboxResult.compileLog(), judgements);
	}

	// JS는 힙 부족 후에도 시간 제한까지 남아 있다가 강제 종료되므로, 메모리 초과 여부를 시간 초과보다 먼저 본다.
	static Verdict classify(TestRunResult run, JsonNode expected) {
		if (run.oomKilled() || run.stderr().contains("java.lang.OutOfMemoryError") || run.stderr().contains("heap out of memory")) {
			return Verdict.MLE;
		}
		if (run.exitCode() == TIMEOUT_KILL_EXIT_CODE) {
			return Verdict.TLE;
		}
		if (run.exitCode() != 0) {
			return Verdict.RE;
		}
		return matches(expected, run.returnValue()) ? Verdict.AC : Verdict.WA;
	}

	// 실수는 언어마다 표기가 달라(Java 2.0, JS 2) 값으로 비교하고, 절대 또는 상대 오차 1e-6까지 같다고 본다.
	static boolean matches(JsonNode expected, JsonNode actual) {
		if (actual == null) {
			return false;
		}
		if (expected.isNumber() && actual.isNumber()) {
			if (expected.isIntegralNumber() && actual.isIntegralNumber()) {
				return expected.bigIntegerValue().equals(actual.bigIntegerValue());
			}
			double difference = Math.abs(expected.doubleValue() - actual.doubleValue());
			return difference <= FLOATING_POINT_TOLERANCE * Math.max(1, Math.abs(expected.doubleValue()));
		}
		if (expected.isArray() && actual.isArray()) {
			if (expected.size() != actual.size()) {
				return false;
			}
			for (int index = 0; index < expected.size(); index++) {
				if (!matches(expected.get(index), actual.get(index))) {
					return false;
				}
			}
			return true;
		}
		return expected.equals(actual);
	}

}
