package com.codingtest.submission;

/**
 * "제출이 접수됐다"는 알림. SubmissionService#submit 이 트랜잭션 안에서 남기고,
 * 트랜잭션이 커밋된 뒤 SubmissionJudgeWorker 가 받아 채점을 시작한다.
 * 서버 재시작 복구(JudgeRecovery)도 남은 제출마다 이 알림을 다시 남겨 같은 길로 채점한다.
 */
public record SubmissionCreatedEvent(long submissionId) {
}
