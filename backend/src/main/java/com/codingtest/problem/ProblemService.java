package com.codingtest.problem;

import com.codingtest.user.UserSolvedProblemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ProblemService {

	private final ProblemRepository problemRepository;
	private final TestCaseRepository testCaseRepository;
	private final UserSolvedProblemRepository userSolvedProblemRepository;

	public ProblemService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
			UserSolvedProblemRepository userSolvedProblemRepository) {
		this.problemRepository = problemRepository;
		this.testCaseRepository = testCaseRepository;
		this.userSolvedProblemRepository = userSolvedProblemRepository;
	}

	/**
	 * @param userId 로그인했으면 users.id, 게스트면 null (그때는 모든 문제가 solved = false)
	 */
	public List<ProblemSummaryResponse> findAll(Long userId) {
		Set<Long> solved = userId == null ? Set.of() : userSolvedProblemRepository.findProblemIdsByUserId(userId);
		return problemRepository.findAll(Sort.by("id")).stream()
				.map(problem -> ProblemSummaryResponse.of(problem, solved.contains(problem.getId())))
				.toList();
	}

	public ProblemDetailResponse getDetail(long id) {
		return ProblemDetailResponse.from(getProblem(id));
	}

	public Problem getProblem(long id) {
		return problemRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문제가 없습니다: " + id));
	}

	public List<TestCase> getTestCases(long problemId) {
		return testCaseRepository.findByProblemIdOrderByHiddenAscSequenceAsc(problemId);
	}

	public List<TestCaseOrder> getTestCaseOrder(long problemId) {
		return testCaseRepository.findOrderByProblemId(problemId);
	}

	public List<TestCase> getExamples(long problemId) {
		return testCaseRepository.findByProblemIdAndHiddenFalseOrderBySequenceAsc(problemId);
	}

}
