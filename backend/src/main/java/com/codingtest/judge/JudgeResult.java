package com.codingtest.judge;

import java.util.List;

/**
 * verdict 는 전체 판정이다: 컴파일 실패면 CE, 모두 통과면 AC, 아니면 처음 실패한 테스트의 판정.
 */
public record JudgeResult(Verdict verdict, String compileLog, List<TestJudgement> tests) {

	public long passedCount() {
		return tests.stream().filter(test -> test.verdict() == Verdict.AC).count();
	}

	public double score() {
		if (tests.isEmpty()) {
			return 0;
		}
		return Math.round(passedCount() * 1000.0 / tests.size()) / 10.0;
	}

}
