package com.codingtest.judge;

import com.codingtest.run.RunRepository;
import com.codingtest.submission.SubmissionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 제출·실행을 접수하기 전에 "지금 받아도 되는가"를 검사한다 (입장 제한, admission control).
 *
 * <p>JudgeSlots 는 동시에 "채점하는" 수만 제한한다. 기다리는 쪽은 제한이 없어서, 이것만 있으면 대기가 끝없이 길어질 수 있다.
 * 그래서 접수 단계에서 두 가지를 본다. 넘으면 거절하고 저장하지 않는다.
 * <ol>
 *   <li>유저별 진행 중 개수: 그 유저의 끝나지 않은(PENDING·JUDGING) 제출+실행이 perUserLimit 이상이면 409 Conflict 로 거절.
 *       한 사람이 연타로 대기열을 차지하지 못하게 한다. 화면이 "내 채점이 아직 진행 중"과 "서버가 붐빔"을 구분해 보여 주도록 429 와 다른 코드를 쓴다.</li>
 *   <li>대기열 상한: 전체의 끝나지 않은 제출+실행이 queueCapacity 이상이면 429 Too Many Requests 로 거절.
 *       맨 뒤 사람이 너무 오래(1분 이상) 기다리게 되느니 "지금은 붐빈다"를 바로 알려 주는 편이 낫다.</li>
 * </ol>
 *
 * <p>한계 (README 7장): "개수를 세고 → 저장"이 한 번에 일어나지 않는다(check-then-act).
 * 같은 순간에 요청 두 개가 들어오면 둘 다 "1건이니 괜찮다"고 보고 둘 다 저장될 수 있다. 상한을 몇 건 넘을 수 있다는 뜻이고,
 * 서버를 지키는 데는 지장이 없어 지금은 허용한다.
 */
@Component
public class JudgeAdmission {

	private static final List<JudgeStatus> UNFINISHED = List.of(JudgeStatus.PENDING, JudgeStatus.JUDGING);

	private final SubmissionRepository submissionRepository;
	private final RunRepository runRepository;
	private final int queueCapacity;
	private final int perUserLimit;

	public JudgeAdmission(SubmissionRepository submissionRepository, RunRepository runRepository,
			@Value("${judge.queue-capacity}") int queueCapacity, @Value("${judge.per-user-unfinished-limit}") int perUserLimit) {
		this.submissionRepository = submissionRepository;
		this.runRepository = runRepository;
		this.queueCapacity = queueCapacity;
		this.perUserLimit = perUserLimit;
	}

	public void checkAcceptable(long userId) {
		long userUnfinished = submissionRepository.countByUserIdAndStatusIn(userId, UNFINISHED)
				+ runRepository.countByUserIdAndStatusIn(userId, UNFINISHED);
		if (userUnfinished >= perUserLimit) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"채점이 끝나지 않은 제출·실행이 이미 " + userUnfinished + "건 있습니다. 끝난 뒤 다시 시도하세요");
		}
		long totalUnfinished = submissionRepository.countByStatusIn(UNFINISHED) + runRepository.countByStatusIn(UNFINISHED);
		if (totalUnfinished >= queueCapacity) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "지금은 채점 대기가 많습니다. 잠시 뒤 다시 시도하세요");
		}
	}

}
