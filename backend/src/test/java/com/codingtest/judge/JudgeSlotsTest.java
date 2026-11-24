package com.codingtest.judge;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Docker 없이 JudgeSlots(세마포어)만 검사한다. 채점 대신 잠깐 잠드는 작업을 넣는다.
 */
class JudgeSlotsTest {

	@Test
	@DisplayName("작업 8개를 한꺼번에 넣어도 동시에 도는 작업은 자리 수(2)를 넘지 않고, 결국 모두 끝난다")
	void limitsConcurrentJudging() {
		JudgeSlots judgeSlots = new JudgeSlots(2, new SimpleMeterRegistry());
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
		JudgeSlots judgeSlots = new JudgeSlots(1, new SimpleMeterRegistry());

		assertThatThrownBy(() -> judgeSlots.runInSlot(() -> {
			throw new IllegalStateException("Docker 응답 없음");
		})).isInstanceOf(IllegalStateException.class);

		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> judgeSlots.runInSlot(() -> { }));
	}

	@Test
	@DisplayName("지표: 자리 1개를 쥔 동안 다음 작업은 대기 수로 잡히고, 끝나면 대기·사용 수가 0으로 돌아오며 시간이 기록된다")
	void recordsSlotMetrics() throws Exception {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		JudgeSlots judgeSlots = new JudgeSlots(1, meterRegistry);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
			workers.submit(() -> {
				judgeSlots.runInSlot(() -> {
					holding.countDown();
					awaitQuietly(release);
				});
				return null;
			});
			holding.await();
			workers.submit(() -> {
				judgeSlots.runInSlot(() -> { });
				return null;
			});

			await().atMost(Duration.ofSeconds(1)).until(() -> gauge(meterRegistry, "judge.slots.waiting") == 1);
			assertThat(gauge(meterRegistry, "judge.slots.in.use")).isEqualTo(1);
			release.countDown();
		}

		assertThat(gauge(meterRegistry, "judge.slots.waiting")).isZero();
		assertThat(gauge(meterRegistry, "judge.slots.in.use")).isZero();
		assertThat(meterRegistry.get("judge.slot.wait").timer().count()).isEqualTo(2);
		assertThat(meterRegistry.get("judge.slot.run").timer().count()).isEqualTo(2);
	}

	private static double gauge(SimpleMeterRegistry meterRegistry, String name) {
		return meterRegistry.get(name).gauge().value();
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
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
