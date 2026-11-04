package com.codingtest.judge;

import java.time.Duration;

/**
 * 문제별 실행 제한. 시간은 테스트 하나당, 메모리는 컨테이너 전체(런타임 자체 사용분 포함) 기준이다.
 */
public record ExecutionLimits(Duration timeLimit, int memoryLimitMb) {

	public static final ExecutionLimits DEFAULT = new ExecutionLimits(Duration.ofSeconds(3), 265);

	// 런타임 자체가 약 55MB를 쓰고 javac도 같은 한도 안에서 돌아야 해서, 이보다 낮으면 컴파일 단계에서 강제 종료될 수 있다.
	private static final int MINIMUM_MEMORY_LIMIT_MB = 128;

	public ExecutionLimits {
		if (timeLimit.isNegative() || timeLimit.isZero()) {
			throw new IllegalArgumentException("시간 제한은 0보다 커야 합니다: " + timeLimit);
		}
		if (memoryLimitMb < MINIMUM_MEMORY_LIMIT_MB) {
			throw new IllegalArgumentException("메모리 제한은 " + MINIMUM_MEMORY_LIMIT_MB + "MB 이상이어야 합니다: " + memoryLimitMb);
		}
	}

}
