package com.codingtest.submission;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * [제출] 한 건. 상태는 PENDING → JUDGING → DONE/FAILED 로만 바뀌고, 바꾸는 메서드도 그 순서대로 하나씩 있다.
 *
 * <p>userId·problemId 를 @ManyToOne 객체가 아니라 id 값으로 둔 이유:
 * 제출을 다루는 코드는 유저·문제 엔티티를 거의 쓰지 않는다 (채점에 필요한 문제·테스트는 ProblemService 로 따로 읽는다).
 * 객체로 두면 저장할 때마다 프록시를 만들어 넣어야 하고, 쓸 일 없는 연관을 따라갈 여지만 생긴다. FK 제약은 DB 가 지킨다.
 */
@Entity
@Table(name = "submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	private Long problemId;

	// EnumType.STRING: DB 에 "JAVA" 같은 이름으로 저장한다. 기본값(ORDINAL)은 0, 1 같은 순번이라 enum 순서를 바꾸면 기존 데이터의 뜻이 바뀐다.
	@Enumerated(EnumType.STRING)
	private Language language;

	@Column(columnDefinition = "mediumtext")
	private String sourceCode;

	@Enumerated(EnumType.STRING)
	private JudgeStatus status;

	// 아래 넷은 DONE 일 때만 값이 있다 (V1 의 CHECK 제약 ck_submissions_done_result 가 DB 에서도 강제한다).
	@Enumerated(EnumType.STRING)
	private Verdict verdict;

	// DECIMAL(4,1). double 은 66.7 을 정확히 표현하지 못해 금액·점수처럼 소수 자릿수가 정해진 값은 BigDecimal 로 다룬다.
	private BigDecimal score;

	@Column(columnDefinition = "text")
	private String compileLog;

	private Instant createdAt;

	private Instant judgedAt;

	Submission(long userId, long problemId, Language language, String sourceCode, Instant createdAt) {
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
		this.score = BigDecimal.valueOf(result.score());
		// 컴파일에 성공해도 경고가 담길 수 있지만, 로그는 CE 일 때만 남긴다 (CHECK 제약 ck_submissions_compile_log).
		this.compileLog = result.verdict() == Verdict.CE ? result.compileLog() : null;
		this.judgedAt = judgedAt;
	}

	void fail() {
		this.status = JudgeStatus.FAILED;
	}

}
