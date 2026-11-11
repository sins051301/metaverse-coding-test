package com.codingtest.run;

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
 * 실행 한 건의 예제 하나 결과. PK 는 (run_id, test_case_id). 예제는 공개라 반환값·출력·에러를 항상 저장한다.
 * 나중에 유저가 직접 추가한 테스트 입력의 결과는 test_cases 를 가리키지 않으므로 별도 테이블(run_custom_test_results)에 둔다.
 */
@Entity
@Table(name = "run_test_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunTestResult implements StoredTestResult {

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

	static RunTestResult of(long runId, TestJudgement judgement) {
		RunTestResult result = new RunTestResult();
		result.id = new Id(runId, judgement.testCaseId());
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
	public record Id(Long runId, Long testCaseId) {
	}

}
