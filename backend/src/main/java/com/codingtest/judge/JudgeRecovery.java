package com.codingtest.judge;

import com.codingtest.run.RunCreatedEvent;
import com.codingtest.run.RunService;
import com.codingtest.submission.SubmissionCreatedEvent;
import com.codingtest.submission.SubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 서버가 켜질 때 끝나지 않은 제출·실행을 다시 채점한다.
 *
 * <p>왜 필요한가: "이 제출을 채점해 줘"라는 전달은 서버 메모리(작업 스레드)에만 있다.
 * 채점 도중 서버가 꺼지면 그 전달은 사라지고, DB 에는 PENDING(아직 시작 전)이나 JUDGING(채점 중이었음)으로 남는다.
 * 그대로 두면 클라이언트는 영원히 결과를 받지 못한다.
 *
 * <p>어떻게: 서버 준비가 끝나면(ApplicationReadyEvent) 남은 id 마다 "접수됨" 이벤트를 다시 남긴다.
 * 트랜잭션 밖에서 남긴 이벤트라 작업자의 fallbackExecution = true 덕분에 바로 작업 스레드로 넘어가고, 처음 접수 때와 같은 길로 채점된다.
 * JUDGING 이었던 것도 결과는 채점이 끝날 때 한 트랜잭션으로만 저장되므로 반쯤 저장된 결과가 없다. 그래서 처음부터 다시 채점하면 된다.
 *
 * <p>한계: 서버가 여러 대면 모든 서버가 같은 행을 다시 채점한다. 서버 여러 대가 되면 메시지 큐로 옮긴다 (README 2장).
 */
@Component
public class JudgeRecovery {

	private static final Logger log = LoggerFactory.getLogger(JudgeRecovery.class);

	private final SubmissionService submissionService;
	private final RunService runService;
	private final ApplicationEventPublisher eventPublisher;

	public JudgeRecovery(SubmissionService submissionService, RunService runService, ApplicationEventPublisher eventPublisher) {
		this.submissionService = submissionService;
		this.runService = runService;
		this.eventPublisher = eventPublisher;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void resumeUnfinished() {
		List<Long> submissionIds = submissionService.findUnfinishedIds();
		List<Long> runIds = runService.findUnfinishedIds();
		if (!submissionIds.isEmpty() || !runIds.isEmpty()) {
			log.info("끝나지 않은 채점을 다시 시작합니다: 제출 {}, 실행 {}", submissionIds, runIds);
		}
		submissionIds.forEach(id -> eventPublisher.publishEvent(new SubmissionCreatedEvent(id)));
		runIds.forEach(id -> eventPublisher.publishEvent(new RunCreatedEvent(id)));
	}

}
