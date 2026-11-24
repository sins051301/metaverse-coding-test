package com.codingtest;

import javax.management.JMException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 가상 스레드와 플랫폼 스레드를 비교하려고 JVM 이 스스로 집계하는 값을 읽는다.
 *
 * <p>플랫폼 스레드 수: ThreadMXBean 은 OS 스레드에 묶인 플랫폼 스레드만 센다. 가상 스레드는 세지 않는다.
 * <p>스레드 스택: NMT(Native Memory Tracking)의 Thread 항목. 플랫폼 스레드 스택이 힙 밖에서 차지한 메모리다.
 * 예약(reserved)은 주소 공간만 잡아 둔 크기, 커밋(committed)은 실제로 메모리를 받은 크기다. -XX:NativeMemoryTracking=summary 가 필요하다.
 * <p>힙: 가상 스레드는 멈춰 있는 동안 스택을 힙에 둔다. 그래서 가상 스레드 쪽 비용은 NMT 가 아니라 힙에서 보인다.
 */
public final class JvmThreadMetrics {

	private static final Pattern NMT_THREAD = Pattern.compile("Thread \\(reserved=(\\d+)KB, committed=(\\d+)KB\\)");

	private JvmThreadMetrics() {
	}

	public record Snapshot(int platformThreads, long threadStackReservedKb, long threadStackCommittedKb, long heapUsedKb) {

		public Snapshot minus(Snapshot baseline) {
			return new Snapshot(platformThreads - baseline.platformThreads,
					threadStackReservedKb - baseline.threadStackReservedKb,
					threadStackCommittedKb - baseline.threadStackCommittedKb,
					heapUsedKb - baseline.heapUsedKb);
		}

		public Snapshot max(Snapshot other) {
			return new Snapshot(Math.max(platformThreads, other.platformThreads),
					Math.max(threadStackReservedKb, other.threadStackReservedKb),
					Math.max(threadStackCommittedKb, other.threadStackCommittedKb),
					Math.max(heapUsedKb, other.heapUsedKb));
		}

		public String describe() {
			return String.format("플랫폼 스레드 %+d개, 스레드 스택(NMT) 예약 %+.1fMB / 커밋 %+.1fMB, 힙 %+.1fMB",
					platformThreads, threadStackReservedKb / 1024.0, threadStackCommittedKb / 1024.0, heapUsedKb / 1024.0);
		}
	}

	public static Snapshot snapshot() {
		long[] threadMemory = readThreadNativeMemoryKb();
		return new Snapshot(
				ManagementFactory.getThreadMXBean().getThreadCount(),
				threadMemory[0],
				threadMemory[1],
				ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024);
	}

	// GC 로 이미 죽은 객체를 치운 뒤 재야 "살아 있는 스레드가 붙잡고 있는 힙"만 남는다.
	public static Snapshot snapshotAfterGc() {
		System.gc();
		return snapshot();
	}

	private static long[] readThreadNativeMemoryKb() {
		try {
			String summary = (String) ManagementFactory.getPlatformMBeanServer().invoke(
					new ObjectName("com.sun.management:type=DiagnosticCommand"),
					"vmNativeMemory",
					new Object[] {new String[] {"summary"}},
					new String[] {String[].class.getName()});
			Matcher matcher = NMT_THREAD.matcher(summary);
			if (!matcher.find()) {
				throw new IllegalStateException("NMT 가 꺼져 있습니다. -XX:NativeMemoryTracking=summary 로 실행하세요:\n" + summary);
			}
			return new long[] {Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))};
		} catch (JMException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/**
	 * 측정하는 동안 100ms 마다 snapshot 을 떠서 가장 큰 값을 기억한다. 샘플러 자신도 플랫폼 스레드 1개라 두 모드에 똑같이 더해진다.
	 */
	public static final class PeakSampler implements AutoCloseable {

		private final Thread sampler;
		private volatile Snapshot peak;
		private volatile boolean running = true;

		public PeakSampler() {
			peak = snapshot();
			sampler = Thread.ofPlatform().daemon().name("metrics-sampler").start(() -> {
				while (running) {
					Snapshot current = snapshot();
					peak = peak.max(current);
					try {
						Thread.sleep(100);
					} catch (InterruptedException exception) {
						return;
					}
				}
			});
		}

		public Snapshot peak() {
			return peak;
		}

		@Override
		public void close() throws InterruptedException {
			running = false;
			sampler.join();
		}
	}

}
