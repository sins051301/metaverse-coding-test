package com.codingtest.submission;

import com.codingtest.judge.StoredTestResult;
import com.codingtest.judge.TestJudgement;
import com.codingtest.judge.Verdict;
import com.codingtest.problem.JsonNodeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 제출 한 건의 테스트 하나 결과. PK 는 (submission_id, test_case_id) 이고,
 * 입력·기댓값·순서는 test_cases 에 있으므로 여기 복사하지 않는다 (README 8장 "test_number 제거").
 * 히든 테스트는 actual·userOutput·errorMessage 가 null 이다 (Judge 가 이미 비워서 넘긴다).
 */
@Entity
@Table(name = "submission_test_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionTestResult implements StoredTestResult {

	@EmbeddedId
	private Id id;

	@Enumerated(EnumType.STRING)
	private Verdict verdict;

	private int timeMs;

	private int memoryKb;

	@Convert(converter = JsonNodeConverter.class)
	@Column(columnDefinition = "json")
	private JsonNode actual;

	@Column(columnDefinition = "text")
	private String userOutput;

	@Column(columnDefinition = "text")
	private String errorMessage;

	static SubmissionTestResult of(long submissionId, TestJudgement judgement) {
		SubmissionTestResult result = new SubmissionTestResult();
		result.id = new Id(submissionId, judgement.testCaseId());
		result.verdict = judgement.verdict();
		result.timeMs = StoredTestResult.toMilliseconds(judgement.timeSeconds());
		result.memoryKb = (int) judgement.memoryKb();
		result.actual = judgement.actual();
		result.userOutput = StoredTestResult.truncate(judgement.userOutput());
		result.errorMessage = StoredTestResult.truncate(judgement.errorMessage());
		return result;
	}

	@Override
	public long getTestCaseId() {
		return id.testCaseId();
	}

	@Embeddable
	public record Id(Long submissionId, Long testCaseId) {
	}

}
