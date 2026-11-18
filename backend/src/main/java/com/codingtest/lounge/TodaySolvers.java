package com.codingtest.lounge;

import com.codingtest.judge.Verdict;
import com.codingtest.submission.SubmissionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/**
 * 오늘(한국 시각 0시부터) 문제를 하나라도 맞힌 회원. 라운지 칠판의 "오늘 N명이 풀었어요"에 쓴다.
 *
 * <p>입장할 때마다 DB 를 세지 않으려고 메모리에 회원 id 를 모은다. 날짜가 바뀐 뒤 처음 물을 때(서버를 막 켰을 때도) 한 번만 DB 에서
 * 오늘 AC 를 받은 회원을 읽어 오고, 그다음은 AC 알림(onSubmissionAccepted)마다 더한다. 그래서 서버를 다시 켜도 수가 0 으로 돌아가지 않는다.
 *
 * <p>한계: 서버가 여러 대면 각자 센다 (LoungeRegistry 와 같은 한계, README 10장).
 */
@Component
class TodaySolvers {

	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final SubmissionRepository submissionRepository;
	private final Set<Long> userIds = new HashSet<>();
	private LocalDate day;

	TodaySolvers(SubmissionRepository submissionRepository) {
		this.submissionRepository = submissionRepository;
	}

	synchronized int count() {
		refreshIfNewDay();
		return userIds.size();
	}

	// @return 더한 뒤의 수
	synchronized int add(long userId) {
		refreshIfNewDay();
		userIds.add(userId);
		return userIds.size();
	}

	private void refreshIfNewDay() {
		LocalDate today = LocalDate.now(KOREA);
		if (today.equals(day)) {
			return;
		}
		day = today;
		userIds.clear();
		userIds.addAll(submissionRepository.findUserIdsByVerdictJudgedSince(Verdict.AC, today.atStartOfDay(KOREA).toInstant()));
	}

}
