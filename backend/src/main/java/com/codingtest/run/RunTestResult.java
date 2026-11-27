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
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import tools.jackson.databind.JsonNode;

/**
 * 실행 한 건의 예제 하나 결과. PK 는 (run_id, test_case_id). 예제는 공개라 반환값·출력·에러를 항상 저장한다.
 * 나중에 유저가 직접 추가한 테스트 입력의 결과는 test_cases 를 가리키지 않으므로 별도 테이블(run_custom_test_results)에 둔다.
 */
@Entity
@Table(name = "run_test_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunTestResult implements StoredTestResult, Persistable<RunTestResult.Id> {

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

	// PK 를 직접 넣는 엔티티라 Spring Data 는 id 가 있다는 이유로 기존 행으로 보고 save 를 merge(행마다 SELECT 후 INSERT)로 처리한다.
	// 결과는 채점이 끝난 뒤 한 번만 새로 저장하므로, 아직 DB 에 없다는 것을 알려 persist(INSERT 만)로 가게 한다.
	@Transient
	@Getter(AccessLevel.NONE)
	private boolean persisted;

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
	public boolean isNew() {
		return !persisted;
	}

	@PostPersist
	@PostLoad
	void markPersisted() {
		persisted = true;
	}

	@Override
	public long getTestCaseId() {
		return id.testCaseId();
	}

	@Embeddable
	public record Id(Long runId, Long testCaseId) {
	}

}
