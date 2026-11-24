package com.codingtest.judge;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * 동시에 채점(Docker 컨테이너 실행)할 수 있는 수를 제한하는 "채점 자리".
 *
 * <p>세마포어(Semaphore) = 허가증 N장을 가진 관리인. 채점하려는 스레드는 허가증을 한 장 받아야(acquire) 들어갈 수 있고,
 * 다 쓰면 돌려준다(release). 허가증이 다 나갔으면 다음 스레드는 누가 돌려줄 때까지 그 자리에서 기다린다.
 * 그래서 제출이 100건 몰려도 컨테이너는 최대 N개만 동시에 뜬다. 자세한 설명과 선택 이유는 README 7장 "동시 채점 수 제한".
 *
 * <p>공정 모드(fair = true): 기다리는 순서대로(먼저 온 스레드부터) 허가증을 준다. 기본값(false)이면 막 도착한 스레드가
 * 오래 기다린 스레드보다 먼저 받을 수 있어(새치기), 운 나쁜 제출이 계속 밀릴 수 있다.
 *
 * <p>제출과 실행이 이 빈 하나를 함께 쓴다. 그래서 둘을 합쳐 N건까지만 동시에 채점된다.
 */
@Component
public class JudgeSlots {

	// 기다림·채점 시간이 수십 초~수 분까지 갈 수 있어(TLE 코드가 자리를 잡으면) 히스토그램 구간을 기본값(30초)보다 넓힌다
	private static final Duration MAXIMUM_EXPECTED_DURATION = Duration.ofMinutes(5);

	private final Semaphore semaphore;
	private final Timer waitTimer;
	private final Timer runTimer;

	public JudgeSlots(@Value("${judge.concurrency}") int concurrency, MeterRegistry meterRegistry) {
		this.semaphore = new Semaphore(concurrency, true);
		Gauge.builder("judge.slots.waiting", semaphore, Semaphore::getQueueLength)
				.description("채점 자리를 기다리며 PENDING 으로 멈춰 있는 제출·실행 수 (대기열 길이)")
				.register(meterRegistry);
		Gauge.builder("judge.slots.in.use", semaphore, slots -> concurrency - slots.availablePermits())
				.description("지금 채점 중인(컨테이너를 쓰는) 제출·실행 수. 최대 judge.concurrency")
				.register(meterRegistry);
		Gauge.builder("judge.slots.capacity", () -> concurrency)
				.description("동시에 채점할 수 있는 최대 수 (judge.concurrency)")
				.register(meterRegistry);
		this.waitTimer = Timer.builder("judge.slot.wait")
				.description("접수 뒤 채점 자리를 받기까지 기다린 시간")
				.publishPercentileHistogram()
				.maximumExpectedValue(MAXIMUM_EXPECTED_DURATION)
				.register(meterRegistry);
		this.runTimer = Timer.builder("judge.slot.run")
				.description("자리를 받은 뒤 채점(컨테이너 실행 + 결과 저장)에 걸린 시간")
				.publishPercentileHistogram()
				.maximumExpectedValue(MAXIMUM_EXPECTED_DURATION)
				.register(meterRegistry);
	}

	/**
	 * 자리가 날 때까지 기다렸다가 judging 을 실행하고, 끝나면(예외가 나도) 자리를 돌려준다.
	 *
	 * <p>release 를 finally 에 두는 이유: 채점 중 예외가 났는데 허가증을 돌려주지 않으면 그 자리는 영원히 사라진다.
	 * N번 그러면 허가증이 0장이 되어 이후 모든 채점이 끝없이 기다린다.
	 *
	 * @throws InterruptedException 기다리는 동안 스레드가 중단됨 (서버 종료 등). 이때는 허가증을 받지 않았으므로 돌려주지도 않는다.
	 */
	public void runInSlot(Runnable judging) throws InterruptedException {
		Timer.Sample waiting = Timer.start();
		semaphore.acquire();
		waiting.stop(waitTimer);
		try {
			runTimer.record(judging);
		} finally {
			semaphore.release();
		}
	}

}
