package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.user.User;
import com.codingtest.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 서버(무작위 포트)에 STOMP 클라이언트를 여러 개 붙여, 브라우저 여러 개가 라운지에 들어온 상황을 만든다.
 */
@Tag("docker")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoungeWebSocketTest {

	private static final String FRONTEND_ORIGIN = "http://localhost:3000";
	private static final Duration WAIT = Duration.ofSeconds(5);

	@LocalServerPort
	private int port;

	@Autowired
	private UserService userService;

	@Autowired
	private TokenService tokenService;

	private final List<LoungeClient> clients = new ArrayList<>();

	@AfterEach
	void disconnectAll() {
		clients.forEach(LoungeClient::disconnect);
	}

	@Test
	@DisplayName("두 게스트가 들어오면 서로 보이고, 한쪽이 움직이면 다른 쪽에 위치가 전달된다")
	void guestsSeeEachOtherAndMoves() throws Exception {
		LoungeClient first = connect(null);
		String firstId = first.join(100, 120, null);

		LoungeClient second = connect(null);
		String secondId = second.join(200, 150, null);

		JsonNode joined = first.awaitEvent("joined", secondId);
		assertThat(joined.path("avatar").path("member").asBoolean()).isFalse();
		assertThat(joined.path("avatar").path("nickname").asString()).matches("익명_[0-9A-F]{4}");
		assertThat(second.lastSnapshotIds()).contains(firstId, secondId);

		second.send("/app/lounge/move", Map.of("x", 210, "y", 152, "direction", "right", "moving", true));
		JsonNode moved = first.awaitEvent("moved", secondId);
		assertThat(moved.path("avatar").path("x").asInt()).isEqualTo(210);
		assertThat(moved.path("avatar").path("direction").asString()).isEqualTo("right");
	}

	@Test
	@DisplayName("같은 책상에는 한 명만 앉고, 늦게 온 쪽은 거절된다. 먼저 앉은 쪽이 나가면 책상이 비어 다시 앉을 수 있다")
	void deskIsTakenByOneAndFreedOnLeave() throws Exception {
		LoungeClient first = connect(null);
		String firstId = first.join(100, 120, null);
		LoungeClient second = connect(null);
		second.join(200, 150, null);

		first.send("/app/lounge/sit", Map.of("deskId", 3));
		assertThat(second.awaitEvent("sat", firstId).path("avatar").path("deskId").asInt()).isEqualTo(3);

		second.send("/app/lounge/sit", Map.of("deskId", 3));
		assertThat(second.awaitPersonal("sit-rejected").path("deskId").asInt()).isEqualTo(3);

		first.disconnect();
		second.awaitEvent("left", firstId);
		second.send("/app/lounge/sit", Map.of("deskId", 3));
		assertThat(second.awaitEvent("sat", second.myAvatarId).path("avatar").path("deskId").asInt()).isEqualTo(3);
	}

	@Test
	@DisplayName("로그인 쿠키가 있으면 회원 닉네임으로, 틀린 쿠키면 게스트로 들어온다 (신원은 메시지가 아니라 연결이 정한다)")
	void identityComesFromCookie() throws Exception {
		User user = userService.findOrCreate("lounge-test-" + UUID.randomUUID(), "lounge@example.com");
		LoungeClient member = connect("accessToken=" + tokenService.issue(user.getId()));
		String memberId = member.join(100, 120, null);
		JsonNode me = member.snapshotAvatar(memberId);
		assertThat(me.path("member").asBoolean()).isTrue();
		assertThat(me.path("nickname").asString()).isEqualTo(user.getNickname());

		LoungeClient forged = connect("accessToken=not-a-real-token");
		String forgedId = forged.join(100, 120, null);
		assertThat(forged.snapshotAvatar(forgedId).path("member").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("게스트가 회원 닉네임을 흉내 내거나 지도 밖 좌표를 보내면 서버가 바로잡는다")
	void sanitizesGuestInput() throws Exception {
		LoungeClient guest = connect(null);
		guest.send("/app/lounge/join", Map.of("nickname", "사용자_ADMIN1", "coat", "rainbow", "x", 9999, "y", -50, "direction", "sideways"));
		JsonNode me = guest.snapshotAvatar(guest.awaitSnapshot().path("myAvatarId").asString());
		assertThat(me.path("nickname").asString()).matches("익명_[0-9A-F]{4}");
		assertThat(me.path("coat").asString()).isEqualTo("ginger");
		assertThat(me.path("x").asInt()).isEqualTo(320);
		assertThat(me.path("y").asInt()).isEqualTo(30);
		assertThat(me.path("direction").asString()).isEqualTo("down");
	}

	@Test
	@DisplayName("허용한 프론트 주소가 아닌 다른 사이트(Origin)에서는 연결할 수 없다")
	void rejectsOtherOrigins() {
		assertThatThrownBy(() -> connect(null, "http://evil.example")).isInstanceOf(Exception.class);
	}

	private LoungeClient connect(String cookie) throws Exception {
		return connect(cookie, FRONTEND_ORIGIN);
	}

	private LoungeClient connect(String cookie, String origin) throws Exception {
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
		handshakeHeaders.setOrigin(origin);
		if (cookie != null) {
			handshakeHeaders.add("Cookie", cookie);
		}
		StompSession session = stompClient
				.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, new StompSessionHandlerAdapter() {
				})
				.get(WAIT.toSeconds(), TimeUnit.SECONDS);
		LoungeClient client = new LoungeClient(session);
		clients.add(client);
		return client;
	}

	/**
	 * 브라우저 탭 하나 역할. 방송(/topic/lounge)과 나에게 온 메시지(/user/queue/lounge)를 각자 줄에 쌓는다.
	 */
	private static final class LoungeClient {

		private final StompSession session;
		private final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
		private final BlockingQueue<JsonNode> personal = new LinkedBlockingQueue<>();
		private JsonNode snapshot;
		private String myAvatarId;

		LoungeClient(StompSession session) {
			this.session = session;
			session.subscribe("/topic/lounge", collector(events));
			session.subscribe("/user/queue/lounge", collector(personal));
		}

		String join(int x, int y, Integer deskId) throws InterruptedException {
			Map<String, Object> request = new HashMap<>(Map.of("coat", "grey", "x", x, "y", y, "direction", "down"));
			request.put("deskId", deskId);
			send("/app/lounge/join", request);
			return awaitSnapshot().path("myAvatarId").asString();
		}

		JsonNode awaitSnapshot() throws InterruptedException {
			snapshot = awaitPersonal("snapshot");
			myAvatarId = snapshot.path("myAvatarId").asString();
			return snapshot;
		}

		List<String> lastSnapshotIds() {
			return snapshot.path("avatars").valueStream().map(avatar -> avatar.path("id").asString()).toList();
		}

		JsonNode snapshotAvatar(String avatarId) {
			return snapshot.path("avatars").valueStream()
					.filter(avatar -> avatar.path("id").asString().equals(avatarId))
					.findFirst()
					.orElseThrow();
		}

		void send(String destination, Object payload) {
			session.send(destination, payload);
		}

		JsonNode awaitEvent(String type, String avatarId) throws InterruptedException {
			return awaitMatching(events, message -> type.equals(message.path("type").asString())
					&& avatarId.equals(message.path("avatar").path("id").asString()), type + " " + avatarId);
		}

		JsonNode awaitPersonal(String type) throws InterruptedException {
			return awaitMatching(personal, message -> type.equals(message.path("type").asString()), type);
		}

		void disconnect() {
			if (session.isConnected()) {
				session.disconnect();
			}
		}

		private static JsonNode awaitMatching(BlockingQueue<JsonNode> queue, Predicate<JsonNode> matches, String description)
				throws InterruptedException {
			long deadline = System.nanoTime() + WAIT.toNanos();
			while (System.nanoTime() < deadline) {
				JsonNode message = queue.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				if (message != null && matches.test(message)) {
					return message;
				}
			}
			throw new AssertionError(WAIT + " 안에 메시지가 오지 않았다: " + description);
		}

		private static StompFrameHandler collector(BlockingQueue<JsonNode> queue) {
			return new StompFrameHandler() {
				@Override
				public Type getPayloadType(StompHeaders headers) {
					return JsonNode.class;
				}

				@Override
				public void handleFrame(StompHeaders headers, Object payload) {
					queue.add((JsonNode) payload);
				}
			};
		}

	}

}
