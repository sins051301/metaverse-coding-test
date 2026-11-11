package com.codingtest.user;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 회원이 푼 문제. [제출]이 AC 로 끝날 때 기록하고, [완료] 뱃지와 Total Solved(행 개수)에 쓴다.
 * 제출 기록에서 계산할 수도 있지만 자주 조회해서 따로 둔다 (README 8장 "의도한 역정규화").
 * 행은 INSERT IGNORE 로만 만든다 (UserSolvedProblemRepository#recordIfAbsent). 그래서 이 엔티티에는 생성자가 없다.
 */
@Entity
@Table(name = "user_solved_problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSolvedProblem {

	@EmbeddedId
	private Id id;

	// 처음 AC 를 받은 시각. 이미 푼 문제를 다시 맞혀도 바뀌지 않는다.
	private Instant solvedAt;

	@Embeddable
	public record Id(Long userId, Long problemId) {
	}

}
