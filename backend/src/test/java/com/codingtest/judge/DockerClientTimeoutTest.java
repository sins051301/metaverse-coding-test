package com.codingtest.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Docker 엔진이 멈췄을 때 Docker 호출이 영원히 기다리지 않고 응답 시간 제한 안에 실패하는지 본다. 진짜 Docker 는 쓰지 않는다.
 *
 * <p>"멈춘 Docker 엔진" 흉내: 연결은 받아 주지만 요청을 읽기만 하고 응답을 한 바이트도 보내지 않는 가짜 서버(ServerSocket).
 * 엔진 프로세스는 살아 있지만 일을 못 하는 상태(재시작 중, 디스크 가득 참 등)와 같다.
 */
class DockerClientTimeoutTest {

	private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(1);
	private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(1);

	private ServerSocket silentDockerEngine;
	private final List<Socket> acceptedConnections = new CopyOnWriteArrayList<>();

	@BeforeEach
	void startSilentDockerEngine() throws IOException {
		silentDockerEngine = new ServerSocket(0);
		Thread.ofVirtual().start(() -> {
			while (!silentDockerEngine.isClosed()) {
				try {
					// 연결을 받아 두기만 한다. 닫지도, 응답하지도 않는다.
					acceptedConnections.add(silentDockerEngine.accept());
				} catch (IOException closed) {
					return;
				}
			}
		});
	}

	@AfterEach
	void stopSilentDockerEngine() throws IOException {
		silentDockerEngine.close();
		for (Socket connection : acceptedConnections) {
			connection.close();
		}
	}

	@Test
	@DisplayName("Docker 엔진이 응답하지 않으면 응답 시간 제한(1초) 뒤에 예외로 끝난다 (시간 제한이 없으면 영원히 기다린다)")
	void failsWhenDockerEngineDoesNotRespond() {
		DockerClient dockerClient = DockerClientConfiguration.create(
				DefaultDockerClientConfig.createDefaultConfigBuilder()
						.withDockerHost("tcp://127.0.0.1:" + silentDockerEngine.getLocalPort())
						.withDockerTlsVerify(false)
						.build(),
				CONNECTION_TIMEOUT, RESPONSE_TIMEOUT);

		// 시간 제한이 동작하지 않으면 이 테스트가 멈추므로, 테스트 자체에도 상한(10초)을 건다.
		long startNanos = System.nanoTime();
		assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
				assertThatThrownBy(() -> dockerClient.createContainerCmd("coding-test/judge-java").exec())
						.isInstanceOf(RuntimeException.class));
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

		assertThat(acceptedConnections).as("가짜 엔진까지 연결은 됐다 (연결 실패가 아니라 응답 대기에서 끊긴 것)").isNotEmpty();
		assertThat(elapsed).as("응답 시간 제한만큼은 기다렸다").isGreaterThanOrEqualTo(RESPONSE_TIMEOUT.minusMillis(100));
	}

}
