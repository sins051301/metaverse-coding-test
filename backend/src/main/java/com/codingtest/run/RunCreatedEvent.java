package com.codingtest.run;

/**
 * "실행이 접수됐다"는 알림. 동작은 SubmissionCreatedEvent 와 같다.
 */
public record RunCreatedEvent(long runId) {
}
