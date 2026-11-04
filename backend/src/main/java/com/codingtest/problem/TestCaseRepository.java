package com.codingtest.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

	// 예제가 항상 먼저 오도록 hidden(false < true)으로 먼저 정렬한다. Judge 실행 순서와 화면 표시 순서가 이 순서를 따른다.
	List<TestCase> findByProblemIdOrderByHiddenAscSequenceAsc(long problemId);

}
