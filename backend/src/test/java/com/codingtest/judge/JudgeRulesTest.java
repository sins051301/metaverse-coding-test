package com.codingtest.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeRulesTest {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private static JsonNode json(String text) {
		return JSON_MAPPER.readTree(text);
	}

	private static TestRunResult run(int exitCode, boolean oomKilled, String returnValue, String stderr) {
		return new TestRunResult(exitCode, 0.1, 1024, oomKilled, returnValue == null ? null : json(returnValue), "", stderr);
	}

	@Test
	@DisplayName("정상 종료면 반환값 비교로 AC/WA 를 가른다")
	void classifiesByReturnValue() {
		assertThat(Judge.classify(run(0, false, "3", ""), json("3"))).isEqualTo(Verdict.AC);
		assertThat(Judge.classify(run(0, false, "4", ""), json("3"))).isEqualTo(Verdict.WA);
		assertThat(Judge.classify(run(0, false, null, ""), json("3"))).isEqualTo(Verdict.WA);
	}

	@Test
	@DisplayName("강제 종료는 TLE, 메모리 에러는 MLE, 그 외 비정상 종료는 RE")
	void classifiesAbnormalExits() {
		assertThat(Judge.classify(run(137, false, null, ""), json("3"))).isEqualTo(Verdict.TLE);
		assertThat(Judge.classify(run(137, true, null, ""), json("3"))).isEqualTo(Verdict.MLE);
		assertThat(Judge.classify(run(1, false, null, "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space"), json("3")))
				.isEqualTo(Verdict.MLE);
		assertThat(Judge.classify(run(137, false, null, "FATAL ERROR: Reached heap limit Allocation failed - JavaScript heap out of memory"), json("3")))
				.isEqualTo(Verdict.MLE);
		assertThat(Judge.classify(run(1, false, null, "java.lang.ArrayIndexOutOfBoundsException"), json("3"))).isEqualTo(Verdict.RE);
	}

	@Test
	@DisplayName("정수는 정확히, 실수는 오차 1e-6까지, 배열은 원소별로 비교한다")
	void comparesValues() {
		assertThat(Judge.matches(json("9007199254740993"), json("9007199254740993"))).isTrue();
		assertThat(Judge.matches(json("9007199254740993"), json("9007199254740992"))).isFalse();
		assertThat(Judge.matches(json("2.0"), json("2"))).isTrue();
		assertThat(Judge.matches(json("0.6666666666666666"), json("0.6666667"))).isTrue();
		assertThat(Judge.matches(json("0.6666666666666666"), json("0.67"))).isFalse();
		assertThat(Judge.matches(json("[[1, 2], [3]]"), json("[[1, 2], [3]]"))).isTrue();
		assertThat(Judge.matches(json("[1, 2]"), json("[1, 2, 3]"))).isFalse();
		assertThat(Judge.matches(json("\"a3b2c\""), json("\"a3b2c\""))).isTrue();
		assertThat(Judge.matches(json("\"3\""), json("3"))).isFalse();
		assertThat(Judge.matches(json("true"), json("true"))).isTrue();
	}

	@Test
	@DisplayName("점수는 통과 비율을 소수 첫째 자리까지 반올림한다")
	void scoresByPassedRatio() {
		TestJudgement passed = new TestJudgement(1, true, Verdict.AC, 0, 0, null, null, null, null, null);
		TestJudgement failed = new TestJudgement(2, true, Verdict.WA, 0, 0, null, null, null, null, null);

		assertThat(new JudgeResult(Verdict.WA, "", List.of(passed, passed, failed)).score()).isEqualTo(66.7);
		assertThat(new JudgeResult(Verdict.AC, "", List.of(passed)).score()).isEqualTo(100.0);
		assertThat(new JudgeResult(Verdict.CE, "error", List.of()).score()).isZero();
	}

}
