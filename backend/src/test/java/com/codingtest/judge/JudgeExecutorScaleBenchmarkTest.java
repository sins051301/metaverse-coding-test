package com.codingtest.judge;

import com.codingtest.JvmThreadMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 채점 실행기의 구조만 떼어, 작업 수를 늘려 가며 가상 스레드와 플랫폼 스레드를 비교한다.
 *
 * <p>실제 채점은 컨테이너 메모리 때문에 노트북에서 10건 정도가 한계이고, 운영에서도 접수 상한이 50건이다.
 * 그래서 Docker 대신 "자리를 받으면 문이 열릴 때까지 기다린다"로 채점을 흉내 낸다. 실행기·세마포어는 운영과 같은 클래스다.
 *
 * <p>순서: 작업 N개를 넘김 → 4개는 자리를 받고 문 앞에서, 나머지는 세마포어 앞에서 멈춤 → 그 상태를 잰다 → 문을 열어 모두 끝낸다.
 * 실행: ./gradlew threadBenchmark (가상) / ./gradlew threadBenchmark -PjudgeVirtualThreads=false (플랫폼)
 */
@Tag("benchmark")
class JudgeExecutorScaleBenchmarkTest {

	private static final int JUDGE_CONCURRENCY = 4;
	private static final List<Integer> TASK_COUNTS = List.of(50, 1_000, 10_000);
	private static final boolean VIRTUAL_THREADS = Boolean.parseBoolean(System.getProperty("judge.virtual-threads", "true"));

	@Test
	@DisplayName("작업 수별로 모두 대기 중일 때의 스레드 수·메모리와, 넘기기·완료 시간을 출력한다")
	void measuresWaitingTasks() throws InterruptedException {
		for (int taskCount : TASK_COUNTS) {
			measure(taskCount);
		}
	}

	private void measure(int taskCount) throws InterruptedException {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("judge-");
		executor.setVirtualThreads(VIRTUAL_THREADS);
		JudgeSlots judgeSlots = new JudgeSlots(JUDGE_CONCURRENCY, new SimpleMeterRegistry());
		CountDownLatch gate = new CountDownLatch(1);
		CountDownLatch started = new CountDownLatch(taskCount);
		CountDownLatch finished = new CountDownLatch(taskCount);

		JvmThreadMetrics.Snapshot baseline = JvmThreadMetrics.snapshotAfterGc();
		long startNanos = System.nanoTime();
		for (int index = 0; index < taskCount; index++) {
			executor.execute(() -> {
				started.countDown();
				try {
					judgeSlots.runInSlot(() -> awaitUninterruptibly(gate));
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				finished.countDown();
			});
		}
		started.await();
		long dispatchMillis = (System.nanoTime() - startNanos) / 1_000_000;

		// started 는 acquire 직전에 센다. 마지막 스레드들까지 세마포어 앞에 멈출 시간을 준다.
		Thread.sleep(500);
		JvmThreadMetrics.Snapshot waiting = JvmThreadMetrics.snapshotAfterGc().minus(baseline);

		gate.countDown();
		finished.await();
		long totalMillis = (System.nanoTime() - startNanos) / 1_000_000;

		System.out.printf("[규모 실험] %s, 작업 %,d개 | 모두 대기 중: %s | 넘기기 %,dms, 전체 완료 %,dms%n",
				VIRTUAL_THREADS ? "가상 스레드" : "플랫폼 스레드", taskCount, waiting.describe(), dispatchMillis, totalMillis);
	}

	private static void awaitUninterruptibly(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

}
