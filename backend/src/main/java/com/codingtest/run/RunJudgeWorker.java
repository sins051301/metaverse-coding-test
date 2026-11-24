package com.codingtest.run;

import com.codingtest.judge.Judge;
import com.codingtest.judge.JudgeExecutorConfiguration;
import com.codingtest.judge.JudgeMode;
import com.codingtest.judge.JudgeResult;
import com.codingtest.judge.JudgeSlots;
import com.codingtest.problem.Problem;
import com.codingtest.problem.ProblemService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
 * 제출과 다른 점: JudgeMode.RUN 이라 예제만 돌리고, 푼 문제를 기록하지 않는다. 채점 자리(JudgeSlots)는 제출과 함께 쓴다.
 */
@Component
public class RunJudgeWorker {

	private static final Logger log = LoggerFactory.getLogger(RunJudgeWorker.class);

	private final RunRepository runRepository;
	private final RunTestResultRepository runTestResultRepository;
	private final ProblemService problemService;
	private final Judge judge;
	private final JudgeSlots judgeSlots;
	private final TransactionTemplate transactionTemplate;
	private final Counter failures;

	public RunJudgeWorker(RunRepository runRepository, RunTestResultRepository runTestResultRepository,
			ProblemService problemService, Judge judge, JudgeSlots judgeSlots, TransactionTemplate transactionTemplate,
			MeterRegistry meterRegistry) {
		this.runRepository = runRepository;
		this.runTestResultRepository = runTestResultRepository;
		this.problemService = problemService;
		this.judge = judge;
		this.judgeSlots = judgeSlots;
		this.transactionTemplate = transactionTemplate;
		this.failures = Counter.builder("judge.failures")
				.description("Docker 오류 등 시스템 문제로 FAILED 가 된 제출·실행 수 (오답·컴파일 에러는 포함하지 않음)")
				.tag("mode", JudgeMode.RUN.name())
				.register(meterRegistry);
	}

	@Async(JudgeExecutorConfiguration.JUDGE_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onRunCreated(RunCreatedEvent event) {
		try {
			judgeSlots.runInSlot(() -> judge(event.runId()));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.warn("실행 {} 채점 대기 중 중단됨 (다음 서버 시작 때 다시 채점)", event.runId());
		}
	}

	private void judge(long runId) {
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
			failures.increment();
			transactionTemplate.executeWithoutResult(status ->
					runRepository.findById(runId).orElseThrow().fail());
		}
	}

}
