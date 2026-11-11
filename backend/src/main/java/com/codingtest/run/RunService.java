package com.codingtest.run;

import com.codingtest.judge.JudgeStatus;
import com.codingtest.judge.SourceCodeRequest;
import com.codingtest.judge.TestResultResponse;
import com.codingtest.problem.ProblemService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * 흐름과 이유는 SubmissionService 의 주석과 같다: PENDING 저장 → 커밋 → 작업 스레드(RunJudgeWorker)가 채점.
 */
@Service
public class RunService {

	private final RunRepository runRepository;
	private final RunTestResultRepository runTestResultRepository;
	private final ProblemService problemService;
	private final ApplicationEventPublisher eventPublisher;

	public RunService(RunRepository runRepository, RunTestResultRepository runTestResultRepository,
			ProblemService problemService, ApplicationEventPublisher eventPublisher) {
		this.runRepository = runRepository;
		this.runTestResultRepository = runTestResultRepository;
		this.problemService = problemService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public Run run(long userId, long problemId, SourceCodeRequest request) {
		problemService.getProblem(problemId);
		Run run = runRepository.save(new Run(userId, problemId, request.language(), request.sourceCode(), Instant.now()));
		eventPublisher.publishEvent(new RunCreatedEvent(run.getId()));
		return run;
	}

	@Transactional(readOnly = true)
	public RunResponse get(long userId, long runId) {
		Run run = runRepository.findByIdAndUserId(runId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "실행 기록이 없습니다: " + runId));
		if (run.getStatus() != JudgeStatus.DONE) {
			return RunResponse.of(run, List.of());
		}
		List<TestResultResponse> tests = TestResultResponse.inDisplayOrder(
				runTestResultRepository.findByIdRunId(runId),
				problemService.getTestCaseOrder(run.getProblemId()),
				problemService.getExamples(run.getProblemId()));
		return RunResponse.of(run, tests);
	}

	@Transactional(readOnly = true)
	public List<Long> findUnfinishedIds() {
		return runRepository.findIdsByStatusIn(List.of(JudgeStatus.PENDING, JudgeStatus.JUDGING));
	}

}
