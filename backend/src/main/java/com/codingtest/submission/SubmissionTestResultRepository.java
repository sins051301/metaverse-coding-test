package com.codingtest.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, SubmissionTestResult.Id> {

	// 순서 없이 읽는다. 화면 순서는 TestResultResponse#inDisplayOrder 가 테스트 케이스 순서에 맞춰 정한다.
	List<SubmissionTestResult> findByIdSubmissionId(long submissionId);

}
