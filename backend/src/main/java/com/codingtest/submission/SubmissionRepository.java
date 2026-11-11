package com.codingtest.submission;

import com.codingtest.judge.JudgeStatus;
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

}
