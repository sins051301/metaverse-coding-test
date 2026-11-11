package com.codingtest.problem;

/**
 * 테스트 케이스의 id 와 예제/히든 여부만 담은 가벼운 조회 결과.
 * 결과 화면의 순서를 정할 때, 수 MB짜리 input 을 읽지 않으려고 쓴다.
 */
public record TestCaseOrder(long id, boolean hidden) {
}
