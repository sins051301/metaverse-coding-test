package com.codingtest.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Docker 없이 JudgeSlots(세마포어)만 검사한다. 채점 대신 잠깐 잠드는 작업을 넣는다.
 */
class JudgeSlotsTest {

	@Test
	@DisplayName("작업 8개를 한꺼번에 넣어도 동시에 도는 작업은 자리 수(2)를 넘지 않고, 결국 모두 끝난다")
	void limitsConcurrentJudging() {
		JudgeSlots judgeSlots = new JudgeSlots(2);
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();
		AtomicInteger finished = new AtomicInteger();

		try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int index = 0; index < 8; index++) {
				workers.submit(() -> {
					judgeSlots.runInSlot(() -> {
						maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
						sleep(Duration.ofMillis(100));
						running.decrementAndGet();
						finished.incrementAndGet();
					});
					return null;
				});
			}
		}

		assertThat(maxRunning.get()).isEqualTo(2);
		assertThat(finished.get()).isEqualTo(8);
	}

	@Test
	@DisplayName("채점 중 예외가 나도 자리를 돌려준다 (돌려주지 않으면 다음 작업이 영원히 기다린다)")
	void releasesSlotWhenJudgingFails() {
		JudgeSlots judgeSlots = new JudgeSlots(1);

		assertThatThrownBy(() -> judgeSlots.runInSlot(() -> {
			throw new IllegalStateException("Docker 응답 없음");
		})).isInstanceOf(IllegalStateException.class);

		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> judgeSlots.runInSlot(() -> { }));
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

}
