package com.codingtest.judge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 채점을 맡는 "작업 스레드"를 만드는 실행기(Executor).
 *
 * <p>요청 스레드(HTTP 요청을 받아 응답하는 일꾼)가 채점까지 하면 응답이 5초씩 늦어진다.
 * 그래서 요청 스레드는 "접수했다"만 바로 응답하고, 채점은 이 실행기가 만든 작업 스레드에 넘긴다.
 *
 * <p>@EnableAsync: @Async 가 붙은 메서드를 호출하면, 호출한 스레드가 직접 실행하지 않고 실행기에 넘겨 다른 스레드에서 실행되게 한다.
 * 주의: 같은 클래스 안에서 this.메서드() 로 부르면 Spring 프록시를 거치지 않아 @Async 가 무시된다. 반드시 다른 빈에서 불러야 한다.
 *
 * <p>가상 스레드(Java 21+): OS 스레드보다 훨씬 가벼워서 작업마다 새로 만들어도 부담이 거의 없다.
 * 채점 스레드는 대부분의 시간을 Docker 응답을 기다리며 보내는데(블로킹), 가상 스레드는 기다리는 동안 OS 스레드를 놓아줘서 이런 일에 잘 맞는다.
 * 이 실행기는 스레드를 몇 개든 만든다. 동시 채점 수 제한은 실행기가 아니라 작업자 안의 세마포어(JudgeSlots)가 한다.
 * 가상 스레드는 풀에 담아 개수를 제한하지 말고 세마포어로 제한하라는 것이 JDK 의 권장이다 (README 7장 "동시 채점 수 제한").
 */
@Configuration
@EnableAsync
public class JudgeExecutorConfiguration {

	public static final String JUDGE_EXECUTOR = "judgeExecutor";

	@Bean(JUDGE_EXECUTOR)
	TaskExecutor judgeExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("judge-");
		executor.setVirtualThreads(true);
		return executor;
	}

}
