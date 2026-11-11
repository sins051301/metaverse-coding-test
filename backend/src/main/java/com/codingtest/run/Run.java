package com.codingtest.run;

import com.codingtest.judge.JudgeResult;
import com.codingtest.judge.JudgeStatus;
import com.codingtest.judge.Language;
import com.codingtest.judge.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * [코드 실행] 한 건. Submission 과 구조가 같지만 일부러 다른 테이블이다 (README 2장 "실행 기록 저장").
 * 제출과 다른 점: 예제만 채점하고, 점수(score)가 없고, 푼 문제에 반영되지 않는다.
 * userId·problemId 를 id 값으로 둔 이유는 Submission 의 주석과 같다.
 */
@Entity
@Table(name = "runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Run {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	private Long problemId;

	@Enumerated(EnumType.STRING)
	private Language language;

	@Column(columnDefinition = "mediumtext")
	private String sourceCode;

	@Enumerated(EnumType.STRING)
	private JudgeStatus status;

	// DONE 일 때만 값이 있다 (V3 의 CHECK 제약 ck_runs_done_result).
	@Enumerated(EnumType.STRING)
	private Verdict verdict;

	@Column(columnDefinition = "text")
	private String compileLog;

	private Instant createdAt;

	private Instant judgedAt;

	Run(long userId, long problemId, Language language, String sourceCode, Instant createdAt) {
		this.userId = userId;
		this.problemId = problemId;
		this.language = language;
		this.sourceCode = sourceCode;
		this.status = JudgeStatus.PENDING;
		this.createdAt = createdAt;
	}

	void startJudging() {
		this.status = JudgeStatus.JUDGING;
	}

	void complete(JudgeResult result, Instant judgedAt) {
		this.status = JudgeStatus.DONE;
		this.verdict = result.verdict();
		this.compileLog = result.verdict() == Verdict.CE ? result.compileLog() : null;
		this.judgedAt = judgedAt;
	}

	void fail() {
		this.status = JudgeStatus.FAILED;
	}

}
