package com.codingtest.submission;

import com.codingtest.judge.JudgeStatus;
import com.codingtest.judge.Language;
import com.codingtest.judge.TestResultResponse;
import com.codingtest.judge.Verdict;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 제출 조회 응답. status 가 DONE 이 되기 전에는 verdict·score·judgedAt 이 null 이고 tests 가 비어 있다.
 */
public record SubmissionResponse(
		long id,
		long problemId,
		Language language,
		JudgeStatus status,
		Verdict verdict,
		BigDecimal score,
		String compileLog,
		Instant createdAt,
		Instant judgedAt,
		List<TestResultResponse> tests) {

	static SubmissionResponse of(Submission submission, List<TestResultResponse> tests) {
		return new SubmissionResponse(
				submission.getId(),
				submission.getProblemId(),
				submission.getLanguage(),
				submission.getStatus(),
				submission.getVerdict(),
				submission.getScore(),
				submission.getCompileLog(),
				submission.getCreatedAt(),
				submission.getJudgedAt(),
				tests);
	}

}
