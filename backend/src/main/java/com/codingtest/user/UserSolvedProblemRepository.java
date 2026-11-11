package com.codingtest.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Set;

public interface UserSolvedProblemRepository extends JpaRepository<UserSolvedProblem, UserSolvedProblem.Id> {

	/**
	 * 처음 푼 문제면 기록하고, 이미 있으면 아무것도 하지 않는다.
	 *
	 * <p>왜 "있는지 조회 → 없으면 저장"이 아니라 INSERT IGNORE 인가:
	 * 같은 유저가 같은 문제를 거의 동시에 두 번 AC 받으면, 두 작업 스레드가 둘 다 "없음"을 보고 둘 다 저장하려다 PK 충돌이 난다.
	 * INSERT IGNORE 는 "PK 가 이미 있으면 조용히 넘어감"을 DB 가 한 문장 안에서 처리해서 이런 경쟁이 생기지 않는다.
	 * 처음 시각(solved_at)도 자연스럽게 보존된다.
	 */
	@Modifying
	@Query(value = "INSERT IGNORE INTO user_solved_problems (user_id, problem_id, solved_at) VALUES (:userId, :problemId, :solvedAt)",
			nativeQuery = true)
	void recordIfAbsent(long userId, long problemId, Instant solvedAt);

	long countByIdUserId(long userId);

	// 문제 목록의 [완료] 뱃지용. PK (user_id, problem_id) 의 앞 칼럼으로 찾으므로 그 유저 행만 읽는다
	@Query("select solved.id.problemId from UserSolvedProblem solved where solved.id.userId = :userId")
	Set<Long> findProblemIdsByUserId(long userId);

}
