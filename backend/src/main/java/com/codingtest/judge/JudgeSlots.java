package com.codingtest.judge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

	private final Semaphore semaphore;

	public JudgeSlots(@Value("${judge.concurrency}") int concurrency) {
		this.semaphore = new Semaphore(concurrency, true);
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
		semaphore.acquire();
		try {
			judging.run();
		} finally {
			semaphore.release();
		}
	}

}
