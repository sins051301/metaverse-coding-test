package com.codingtest.judge;

import tools.jackson.databind.JsonNode;

/**
 * 테스트 하나의 판정. 히든 테스트는 입력이나 정답이 드러나지 않도록 input/expected/actual/userOutput/errorMessage 를 비운다.
 * 유저 코드가 테스트 입력을 읽어 출력하거나 에러 메시지에 실을 수 있기 때문이다.
 */
public record TestJudgement(
		int testNumber,
		boolean hidden,
		Verdict verdict,
		double timeSeconds,
		long memoryKb,
		JsonNode input,
		JsonNode expected,
		JsonNode actual,
		String userOutput,
		String errorMessage) {
}
