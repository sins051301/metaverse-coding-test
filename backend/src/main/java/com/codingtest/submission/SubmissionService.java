package com.codingtest.submission;

import com.codingtest.judge.JudgeAdmission;
import com.codingtest.judge.JudgeStatus;
import com.codingtest.judge.SourceCodeRequest;
import com.codingtest.judge.TestResultResponse;
import com.codingtest.problem.ProblemService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class SubmissionService {

	private final SubmissionRepository submissionRepository;
	private final SubmissionTestResultRepository submissionTestResultRepository;
	private final ProblemService problemService;
	private final JudgeAdmission judgeAdmission;
	private final ApplicationEventPublisher eventPublisher;

	public SubmissionService(SubmissionRepository submissionRepository, SubmissionTestResultRepository submissionTestResultRepository,
			ProblemService problemService, JudgeAdmission judgeAdmission, ApplicationEventPublisher eventPublisher) {
		this.submissionRepository = submissionRepository;
		this.submissionTestResultRepository = submissionTestResultRepository;
		this.problemService = problemService;
		this.judgeAdmission = judgeAdmission;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * 제출을 PENDING 으로 저장하고 바로 돌아온다. 채점은 하지 않는다.
	 *
	 * <p>흐름: (1) 이 트랜잭션에서 INSERT → (2) "접수됨" 이벤트를 남김 → (3) 메서드가 끝나며 커밋
	 * → (4) 커밋된 뒤에야 SubmissionJudgeWorker 가 이벤트를 받아 작업 스레드에서 채점.
	 *
	 * <p>이벤트를 커밋 "뒤에" 처리하는 이유: 작업 스레드는 별도의 DB 연결로 이 제출을 다시 읽는다.
	 * 커밋 전이면 아직 다른 연결에서 보이지 않아(트랜잭션 격리) "그런 제출이 없다"가 될 수 있다.
	 * 또 저장이 실패해 롤백되면 채점도 시작되지 않아야 한다.
	 *
	 * <p>저장 전에 접수 제한(유저별 진행 중 개수, 대기열 상한)을 넘으면 409·429 로 거절한다 (JudgeAdmission).
	 */
	@Transactional
	public Submission submit(long userId, long problemId, SourceCodeRequest request) {
		problemService.getProblem(problemId);
		judgeAdmission.checkAcceptable(userId);
		Submission submission = submissionRepository.save(
				new Submission(userId, problemId, request.language(), request.sourceCode(), Instant.now()));
		eventPublisher.publishEvent(new SubmissionCreatedEvent(submission.getId()));
		return submission;
	}

	@Transactional(readOnly = true)
	public SubmissionResponse get(long userId, long submissionId) {
		Submission submission = submissionRepository.findByIdAndUserId(submissionId, userId)
				// 남의 제출이면 403 이 아니라 404: 403 이면 "그 번호의 제출이 존재한다"는 사실이 드러난다.
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제출이 없습니다: " + submissionId));
		if (submission.getStatus() != JudgeStatus.DONE) {
			// 채점 전에는 결과가 없다. 폴링할 때마다 테스트 케이스를 읽지 않도록 여기서 끝낸다.
			return SubmissionResponse.of(submission, List.of());
		}
		List<TestResultResponse> tests = TestResultResponse.inDisplayOrder(
				submissionTestResultRepository.findByIdSubmissionId(submissionId),
				problemService.getTestCaseOrder(submission.getProblemId()),
				problemService.getExamples(submission.getProblemId()));
		return SubmissionResponse.of(submission, tests);
	}

	@Transactional(readOnly = true)
	public SubmissionHistoryResponse getHistory(long userId, long problemId, int page, int size) {
		problemService.getProblem(problemId);
		Long totalCount = page == 0 ? submissionRepository.countByUserIdAndProblemId(userId, problemId) : null;
		return SubmissionHistoryResponse.of(submissionRepository.findSummaries(userId, problemId, PageRequest.of(page, size)), totalCount);
	}

	@Transactional(readOnly = true)
	public List<Long> findUnfinishedIds() {
		return submissionRepository.findIdsByStatusIn(List.of(JudgeStatus.PENDING, JudgeStatus.JUDGING));
	}

}
