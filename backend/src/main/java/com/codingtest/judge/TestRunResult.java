package com.codingtest.judge;

import tools.jackson.databind.JsonNode;

/**
 * 테스트 하나의 실행 결과. returnValue 는 래퍼가 정상적으로 결과 JSON을 남겼을 때만 있고, 그 외(예외·시간 초과 등)에는 null 이다.
 */
public record TestRunResult(
		int exitCode,
		double timeSeconds,
		long memoryKb,
		boolean oomKilled,
		JsonNode returnValue,
		String userOutput,
		String stderr) {
}
