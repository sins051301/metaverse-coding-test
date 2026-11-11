package com.codingtest.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Docker 엔진과 HTTP 로 통신하는 클라이언트.
 *
 * <p>시간 제한 두 가지 (값과 근거는 README 7장 "Docker 호출 시간 제한", 조정 목록은 docs/tuning.md):
 * <ul>
 *   <li>connectionTimeout: Docker 엔진에 연결이 맺어질 때까지 기다리는 최대 시간. 엔진이 꺼져 있으면 바로 실패하게 한다.</li>
 *   <li>responseTimeout: 요청을 보낸 뒤 응답 데이터가 오기까지 기다리는 최대 시간.
 *       채점 결과를 받는 attach 스트림은 컨테이너가 끝날 때까지 아무것도 오지 않으므로,
 *       이 값은 가장 오래 걸리는 컨테이너 시간(SandboxRunner#containerTimeout)보다 길어야 한다.</li>
 * </ul>
 * 시간 제한이 없으면 엔진이 멈췄을 때 채점 스레드가 영원히 기다리고, 그 스레드가 쥔 채점 자리(JudgeSlots)도 돌아오지 않는다.
 */
@Configuration
public class DockerClientConfiguration {

	@Bean
	public DockerClient dockerClient(@Value("${judge.docker.connection-timeout}") Duration connectionTimeout,
			@Value("${judge.docker.response-timeout}") Duration responseTimeout) {
		return create(DefaultDockerClientConfig.createDefaultConfigBuilder().build(), connectionTimeout, responseTimeout);
	}

	// 접속 주소(config)를 밖에서 받게 나눈 이유: 테스트가 "응답하지 않는 가짜 Docker 엔진" 주소를 넣어 시간 제한을 확인한다.
	static DockerClient create(DockerClientConfig config, Duration connectionTimeout, Duration responseTimeout) {
		ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
				.dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig())
				.connectionTimeout(connectionTimeout)
				.responseTimeout(responseTimeout)
				.build();
		return DockerClientImpl.getInstance(config, httpClient);
	}

}
