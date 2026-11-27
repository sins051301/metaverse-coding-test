package com.codingtest.run;

import com.codingtest.TestcontainersConfiguration;
import com.codingtest.judge.Language;
import com.codingtest.judge.TestJudgement;
import com.codingtest.judge.Verdict;
import com.codingtest.problem.TestCaseOrder;
import com.codingtest.problem.TestCaseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실행 결과 저장이 MySQL 에 SELECT 없이 INSERT 한 문장으로 가는지 확인한다.
 * 세는 방식은 SubmissionTestResultSaveQueryTest 와 같다.
 */
@Tag("docker")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RunTestResultSaveQueryTest {

	private static final long PROBLEM_ID = 6;

	@Autowired
	private RunRepository runRepository;

	@Autowired
	private RunTestResultRepository runTestResultRepository;

	@Autowired
	private TestCaseRepository testCaseRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("실행의 테스트 결과를 한꺼번에 저장하면 MySQL 이 받는 문장은 INSERT 1개뿐이다")
	void savesAllResultsInOneInsert() {
		long userId = insertUser();
		List<TestCaseOrder> testCases = testCaseRepository.findOrderByProblemId(PROBLEM_ID);
		Run run = runRepository.save(new Run(userId, PROBLEM_ID, Language.JAVA, "class Solution {}", Instant.now()));
		entityManager.flush();
		long selectsBefore = serverStatus("Com_select");
		long insertsBefore = serverStatus("Com_insert");

		runTestResultRepository.saveAll(testCases.stream()
				.map(testCase -> RunTestResult.of(run.getId(), judgement(testCase)))
				.toList());
		entityManager.flush();

		assertThat(serverStatus("Com_select") - selectsBefore).isZero();
		assertThat(serverStatus("Com_insert") - insertsBefore).isEqualTo(1);
		assertThat(savedRowCount(run.getId())).isEqualTo(testCases.size()).isGreaterThan(1);
	}

	private static TestJudgement judgement(TestCaseOrder testCase) {
		return new TestJudgement(0, testCase.id(), false, Verdict.AC, 0.01, 1024, null, null, null, null, null);
	}

	private long insertUser() {
		entityManager.createNativeQuery(
				"INSERT INTO users (google_subject, email, nickname, created_at) VALUES ('subject', 'user@example.com', 'nickname', NOW())")
				.executeUpdate();
		return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
	}

	private long savedRowCount(long runId) {
		return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM run_test_results WHERE run_id = ?1")
				.setParameter(1, runId)
				.getSingleResult()).longValue();
	}

	private long serverStatus(String name) {
		Object[] row = (Object[]) entityManager.createNativeQuery("SHOW SESSION STATUS LIKE ?1")
				.setParameter(1, name)
				.getSingleResult();
		return Long.parseLong(row[1].toString());
	}

}
