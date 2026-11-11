package com.codingtest.submission;

/**
 * 제출이 AC 로 채점되어 결과가 저장된 뒤 남기는 이벤트. 라운지가 듣고 "OO님이 풀었어요"를 알린다.
 * 채점 코드는 라운지를 모르고 이 이벤트만 남긴다. 그래서 라운지를 고치거나 빼도 채점은 그대로다.
 */
public record SubmissionAcceptedEvent(long userId, long problemId) {
}
