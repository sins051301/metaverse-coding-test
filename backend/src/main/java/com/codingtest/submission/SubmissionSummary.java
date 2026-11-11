package com.codingtest.submission;

import com.codingtest.judge.JudgeStatus;
import com.codingtest.judge.Language;
import com.codingtest.judge.Verdict;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 제출 내역 목록의 한 줄. 소스 코드·컴파일 로그·테스트별 결과는 담지 않는다 (자세한 내용은 GET /api/submissions/{id}).
 *
 * <p>SubmissionRepository#findSummaries 가 JPQL 생성자 표현식(select new ...)으로 이 값만 바로 읽는다.
 * 엔티티(Submission)로 읽으면 행마다 source_code(MEDIUMTEXT, 최대 64K자)까지 DB 에서 가져오는데, 목록에서는 쓰지 않는다.
 */
public record SubmissionSummary(
		long id,
		Language language,
		JudgeStatus status,
		Verdict verdict,
		BigDecimal score,
		Instant createdAt,
		Instant judgedAt) {
}
