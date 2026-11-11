package com.codingtest.submission;

import com.codingtest.judge.JudgeStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

	// 본인 제출만 찾는다. 남의 제출 번호로 조회하면 비어 있어서 404 가 된다.
	Optional<Submission> findByIdAndUserId(long id, long userId);

	// 서버 재시작 복구용. status 인덱스(ix_submissions_status)로 찾는다.
	@Query("select submission.id from Submission submission where submission.status in :statuses order by submission.id")
	List<Long> findIdsByStatusIn(Collection<JudgeStatus> statuses);

	// 접수 제한(JudgeAdmission)용. status 인덱스로 센다.
	long countByStatusIn(Collection<JudgeStatus> statuses);

	// (user_id, problem_id) 인덱스의 앞 칼럼 user_id 로 그 유저 행만 찾은 뒤 status 를 거른다.
	long countByUserIdAndStatusIn(long userId, Collection<JudgeStatus> statuses);

	/**
	 * 한 유저가 한 문제에 낸 제출 목록, 최신순.
	 *
	 * <p>인덱스: ix_submissions_user_problem (user_id, problem_id) 하나로 정렬 없이 끝난다.
	 * InnoDB 의 보조 인덱스는 각 항목 끝에 기본키(id)를 붙여 저장하므로, 이 인덱스는 실제로 (user_id, problem_id, id) 순서로 정렬돼 있다.
	 * 그래서 "이 유저·이 문제를 id 내림차순으로"는 인덱스를 뒤에서부터 읽기만 하면 된다 (README 7장 "제출 내역").
	 * created_at 대신 id 로 정렬하는 것도 이 때문이다. id 는 AUTO_INCREMENT 라 늦게 낸 제출일수록 크다.
	 *
	 * <p>Slice: 요청한 개수보다 1개 더 읽어 "다음 페이지가 있는지"만 안다. Page 와 달리 전체 개수를 세는 COUNT 쿼리를 따로 날리지 않는다.
	 */
	@Query("select new com.codingtest.submission.SubmissionSummary(submission.id, submission.language, submission.status, "
			+ "submission.verdict, submission.score, submission.createdAt, submission.judgedAt) "
			+ "from Submission submission where submission.userId = :userId and submission.problemId = :problemId "
			+ "order by submission.id desc")
	Slice<SubmissionSummary> findSummaries(long userId, long problemId, Pageable pageable);

}
