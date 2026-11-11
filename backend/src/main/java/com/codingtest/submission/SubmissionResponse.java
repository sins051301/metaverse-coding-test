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
 * sourceCode 는 제출 내역에서 행을 펼쳐 그때 낸 코드를 보여 줄 때 쓴다 (목록 API 는 가볍게 두려고 코드를 빼고, 펼칠 때 이 API 로 가져온다).
 */
public record SubmissionResponse(
		long id,
		long problemId,
		Language language,
		String sourceCode,
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
				submission.getSourceCode(),
				submission.getStatus(),
				submission.getVerdict(),
				submission.getScore(),
				submission.getCompileLog(),
				submission.getCreatedAt(),
				submission.getJudgedAt(),
				tests);
	}

}
