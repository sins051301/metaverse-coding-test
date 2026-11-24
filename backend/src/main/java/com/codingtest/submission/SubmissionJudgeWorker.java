package com.codingtest.submission;

import com.codingtest.judge.Judge;
import com.codingtest.judge.JudgeExecutorConfiguration;
import com.codingtest.judge.JudgeMode;
import com.codingtest.judge.JudgeResult;
import com.codingtest.judge.JudgeSlots;
import com.codingtest.judge.Verdict;
import com.codingtest.problem.Problem;
import com.codingtest.problem.ProblemService;
import com.codingtest.user.UserSolvedProblemRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * 작업 스레드에서 제출 하나를 채점한다.
 *
 * <p>@TransactionalEventListener(phase = AFTER_COMMIT): SubmissionCreatedEvent 를 남긴 트랜잭션이 "커밋된 뒤에" 이 메서드를 부른다.
 * 롤백되면 부르지 않는다. fallbackExecution = true 는 트랜잭션 없이 남긴 이벤트(서버 재시작 복구)도 바로 처리하라는 뜻이다.
 *
 * <p>@Async(JUDGE_EXECUTOR): 이벤트를 남긴 스레드(요청 스레드)가 직접 채점하지 않고, 채점용 실행기의 작업 스레드에 넘긴다.
 * 그래서 요청 스레드는 채점(수 초)을 기다리지 않고 바로 202 를 응답한다.
 *
 * <p>트랜잭션을 @Transactional 대신 TransactionTemplate 으로 직접 나눈 이유:
 * 채점(Docker 실행)은 수 초가 걸린다. 메서드 전체를 트랜잭션으로 묶으면 그동안 DB 연결을 붙잡고 있어 연결이 금방 바닥난다.
 * 그래서 짧은 트랜잭션 두 개(JUDGING 표시, 결과 저장)로 나누고, 그 사이의 채점은 트랜잭션 밖에서 한다.
 * (같은 클래스 안의 다른 @Transactional 메서드를 this 로 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다는 점도 이유다.)
 *
 * <p>JudgeSlots: 채점 자리(허가증)를 받을 때까지 PENDING 인 채로 기다리고, 자리를 받은 뒤에야 JUDGING 으로 바꾼다.
 * 그래서 화면에서 "대기 중"(PENDING)과 "채점 중"(JUDGING)이 구분된다.
 */
@Component
public class SubmissionJudgeWorker {

	private static final Logger log = LoggerFactory.getLogger(SubmissionJudgeWorker.class);

	private final SubmissionRepository submissionRepository;
	private final SubmissionTestResultRepository submissionTestResultRepository;
	private final UserSolvedProblemRepository userSolvedProblemRepository;
	private final ProblemService problemService;
	private final Judge judge;
	private final JudgeSlots judgeSlots;
	private final TransactionTemplate transactionTemplate;
	private final ApplicationEventPublisher eventPublisher;
	private final Counter failures;

	public SubmissionJudgeWorker(SubmissionRepository submissionRepository, SubmissionTestResultRepository submissionTestResultRepository,
			UserSolvedProblemRepository userSolvedProblemRepository, ProblemService problemService, Judge judge,
			JudgeSlots judgeSlots, TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher,
			MeterRegistry meterRegistry) {
		this.submissionRepository = submissionRepository;
		this.submissionTestResultRepository = submissionTestResultRepository;
		this.userSolvedProblemRepository = userSolvedProblemRepository;
		this.problemService = problemService;
		this.judge = judge;
		this.judgeSlots = judgeSlots;
		this.transactionTemplate = transactionTemplate;
		this.eventPublisher = eventPublisher;
		this.failures = Counter.builder("judge.failures")
				.description("Docker 오류 등 시스템 문제로 FAILED 가 된 제출·실행 수 (오답·컴파일 에러는 포함하지 않음)")
				.tag("mode", JudgeMode.SUBMIT.name())
				.register(meterRegistry);
	}

	@Async(JudgeExecutorConfiguration.JUDGE_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onSubmissionCreated(SubmissionCreatedEvent event) {
		try {
			judgeSlots.runInSlot(() -> judge(event.submissionId()));
		} catch (InterruptedException exception) {
			// 자리를 기다리다 서버가 꺼지는 경우. DB 에는 PENDING 으로 남아 있어 다음 시작 때 JudgeRecovery 가 다시 채점한다.
			Thread.currentThread().interrupt();
			log.warn("제출 {} 채점 대기 중 중단됨 (다음 서버 시작 때 다시 채점)", event.submissionId());
		}
	}

	private void judge(long submissionId) {
		// 트랜잭션 1: JUDGING 으로 표시. 엔티티 값을 바꾸면 커밋할 때 JPA 가 UPDATE 를 만들어 준다 (변경 감지, dirty checking).
		Submission submission = transactionTemplate.execute(status -> {
			Submission found = submissionRepository.findById(submissionId).orElseThrow();
			found.startJudging();
			return found;
		});

		JudgeResult result;
		try {
			// 트랜잭션 밖: 문제·테스트를 읽고(각 조회가 짧은 읽기 트랜잭션) 채점한다. 여기서 수 초가 걸린다.
			Problem problem = problemService.getProblem(submission.getProblemId());
			result = judge.judge(problem, problemService.getTestCases(problem.getId()),
					submission.getLanguage(), submission.getSourceCode(), JudgeMode.SUBMIT);

			// 트랜잭션 2: 결과 저장, DONE 표시, AC 면 푼 문제 기록을 한 번에. 중간에 실패하면 셋 다 취소된다.
			transactionTemplate.executeWithoutResult(status -> {
				Submission judged = submissionRepository.findById(submissionId).orElseThrow();
				Instant now = Instant.now();
				judged.complete(result, now);
				submissionTestResultRepository.saveAll(result.tests().stream()
						.map(test -> SubmissionTestResult.of(submissionId, test))
						.toList());
				if (result.verdict() == Verdict.AC) {
					userSolvedProblemRepository.recordIfAbsent(judged.getUserId(), judged.getProblemId(), now);
				}
			});
		} catch (RuntimeException exception) {
			// 오답·컴파일 에러는 예외가 아니라 정상 결과(DONE)다. 여기 오는 건 Docker 가 응답하지 않는 등 시스템 문제다.
			log.error("제출 {} 채점 실패", submissionId, exception);
			failures.increment();
			transactionTemplate.executeWithoutResult(status ->
					submissionRepository.findById(submissionId).orElseThrow().fail());
			return;
		}

		// 결과가 저장(커밋)된 뒤, try 밖에서 알린다. 알림 쪽(라운지)에서 문제가 나도 채점 결과가 FAILED 로 바뀌지 않게 하려는 것이다
		if (result.verdict() == Verdict.AC) {
			eventPublisher.publishEvent(new SubmissionAcceptedEvent(submission.getUserId(), submission.getProblemId()));
		}
	}

}
