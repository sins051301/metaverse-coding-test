package com.codingtest.run;

import com.codingtest.judge.JudgeResult;
import com.codingtest.judge.TestJudgement;
import com.codingtest.judge.Verdict;

import java.util.List;

public record RunResponse(Verdict verdict, long passedCount, int totalCount, double score, String compileLog, List<TestJudgement> tests) {

	static RunResponse from(JudgeResult result) {
		return new RunResponse(result.verdict(), result.passedCount(), result.tests().size(), result.score(), result.compileLog(), result.tests());
	}

}
