package com.codingtest.run;

import com.codingtest.judge.Judge;
import com.codingtest.judge.JudgeExecutorConfiguration;
import com.codingtest.judge.JudgeMode;
import com.codingtest.judge.JudgeResult;
import com.codingtest.problem.Problem;
import com.codingtest.problem.ProblemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * 작업 스레드에서 실행 하나를 채점한다. 각 어노테이션과 트랜잭션을 나누는 이유는 SubmissionJudgeWorker 의 주석에 자세히 적었다.
 * 제출과 다른 점: JudgeMode.RUN 이라 예제만 돌리고, 푼 문제를 기록하지 않는다.
 */
@Component
public class RunJudgeWorker {

	private static final Logger log = LoggerFactory.getLogger(RunJudgeWorker.class);

	private final RunRepository runRepository;
	private final RunTestResultRepository runTestResultRepository;
	private final ProblemService problemService;
	private final Judge judge;
	private final TransactionTemplate transactionTemplate;

	public RunJudgeWorker(RunRepository runRepository, RunTestResultRepository runTestResultRepository,
			ProblemService problemService, Judge judge, TransactionTemplate transactionTemplate) {
		this.runRepository = runRepository;
		this.runTestResultRepository = runTestResultRepository;
		this.problemService = problemService;
		this.judge = judge;
		this.transactionTemplate = transactionTemplate;
	}

	@Async(JudgeExecutorConfiguration.JUDGE_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onRunCreated(RunCreatedEvent event) {
		long runId = event.runId();
		Run run = transactionTemplate.execute(status -> {
			Run found = runRepository.findById(runId).orElseThrow();
			found.startJudging();
			return found;
		});

		try {
			Problem problem = problemService.getProblem(run.getProblemId());
			JudgeResult result = judge.judge(problem, problemService.getTestCases(problem.getId()),
					run.getLanguage(), run.getSourceCode(), JudgeMode.RUN);

			transactionTemplate.executeWithoutResult(status -> {
				runRepository.findById(runId).orElseThrow().complete(result, Instant.now());
				runTestResultRepository.saveAll(result.tests().stream()
						.map(test -> RunTestResult.of(runId, test))
						.toList());
			});
		} catch (RuntimeException exception) {
			log.error("실행 {} 채점 실패", runId, exception);
			transactionTemplate.executeWithoutResult(status ->
					runRepository.findById(runId).orElseThrow().fail());
		}
	}

}
