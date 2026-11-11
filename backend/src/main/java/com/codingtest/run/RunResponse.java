package com.codingtest.run;

import com.codingtest.judge.JudgeStatus;
import com.codingtest.judge.Language;
import com.codingtest.judge.TestResultResponse;
import com.codingtest.judge.Verdict;

import java.time.Instant;
import java.util.List;

/**
 * 실행 조회 응답. 점수는 저장하지 않으므로, 통과 수(passedCount)를 결과 행에서 바로 센다.
 */
public record RunResponse(
		long id,
		long problemId,
		Language language,
		JudgeStatus status,
		Verdict verdict,
		long passedCount,
		int totalCount,
		String compileLog,
		Instant createdAt,
		Instant judgedAt,
		List<TestResultResponse> tests) {

	static RunResponse of(Run run, List<TestResultResponse> tests) {
		return new RunResponse(
				run.getId(),
				run.getProblemId(),
				run.getLanguage(),
				run.getStatus(),
				run.getVerdict(),
				tests.stream().filter(test -> test.verdict() == Verdict.AC).count(),
				tests.size(),
				run.getCompileLog(),
				run.getCreatedAt(),
				run.getJudgedAt(),
				tests);
	}

}
