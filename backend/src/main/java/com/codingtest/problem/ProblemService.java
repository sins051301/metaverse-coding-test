package com.codingtest.problem;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProblemService {

	private final ProblemRepository problemRepository;
	private final TestCaseRepository testCaseRepository;

	public ProblemService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository) {
		this.problemRepository = problemRepository;
		this.testCaseRepository = testCaseRepository;
	}

	public List<ProblemSummaryResponse> findAll() {
		return problemRepository.findAll(Sort.by("id")).stream().map(ProblemSummaryResponse::from).toList();
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

}
