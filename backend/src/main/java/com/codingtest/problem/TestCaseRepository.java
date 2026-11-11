package com.codingtest.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

	// 예제가 항상 먼저 오도록 hidden(false < true)으로 먼저 정렬한다. Judge 실행 순서와 화면 표시 순서가 이 순서를 따른다.
	List<TestCase> findByProblemIdOrderByHiddenAscSequenceAsc(long problemId);

	// 예제만. 결과 화면에 입력·기댓값을 붙일 때 쓴다 (예제는 입력이 작다).
	List<TestCase> findByProblemIdAndHiddenFalseOrderBySequenceAsc(long problemId);

	// 순서만 담은 가벼운 조회. input·expected(JSON) 컬럼을 읽지 않아 큰 행이 있어도 정렬 버퍼 문제가 없고,
	// (problem_id, hidden, sequence) 인덱스만으로 답할 수 있다.
	@Query("select new com.codingtest.problem.TestCaseOrder(testCase.id, testCase.hidden) from TestCase testCase "
			+ "where testCase.problem.id = :problemId order by testCase.hidden, testCase.sequence")
	List<TestCaseOrder> findOrderByProblemId(long problemId);

}
