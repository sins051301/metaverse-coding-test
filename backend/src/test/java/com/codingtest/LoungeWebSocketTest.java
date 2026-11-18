package com.codingtest;

import com.codingtest.auth.TokenService;
import com.codingtest.submission.SubmissionAcceptedEvent;
import com.codingtest.user.User;
import com.codingtest.user.UserService;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
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
// 유예 시간을 줄여 "돌아오지 않음"을 빨리 확인한다 (실제 값은 application.properties)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "lounge.desk.reconnect-grace=2s")
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

	@Autowired
	private ApplicationEventPublisher eventPublisher;

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
	@DisplayName("앉은 고양이의 타이핑·낮잠은 모두에게 방송되고, 서 있으면 무시되며, 일어나면 idle 로 돌아간다")
	void broadcastsActivityOfSeatedCats() throws Exception {
		LoungeClient typist = connect(null);
		String typistId = typist.join(100, 120, null);
		LoungeClient neighbor = connect(null);
		neighbor.join(200, 150, null);

		typist.send("/app/lounge/activity", Map.of("activity", "typing"));
		typist.send("/app/lounge/sit", Map.of("deskId", 5));
		neighbor.awaitEvent("sat", typistId);
		typist.send("/app/lounge/activity", Map.of("activity", "napping"));
		JsonNode napping = neighbor.awaitEvent("activity", typistId);
		assertThat(napping.path("avatar").path("activity").asString())
				.as("서 있을 때 보낸 typing 은 무시되어 방송되지 않았고, 앉은 뒤의 napping 이 처음 온 활동이다").isEqualTo("napping");

		typist.send("/app/lounge/stand", Map.of());
		assertThat(neighbor.awaitEvent("stood", typistId).path("avatar").path("activity").asString()).isEqualTo("idle");
	}

	@Test
	@DisplayName("제출이 AC 면 그 회원의 아바타에 별을 띄우라는 solved 가 방송되고, 나중에 들어온 사람도 최근 푼 기록을 받는다")
	void broadcastsSolvedFromJudgeResult() throws Exception {
		User user = userService.findOrCreate("lounge-solved-" + UUID.randomUUID(), "solved@example.com");
		LoungeClient member = connect("accessToken=" + tokenService.issue(user.getId()));
		String memberId = member.join(100, 120, null);

		eventPublisher.publishEvent(new SubmissionAcceptedEvent(user.getId(), 1));

		JsonNode solved = member.awaitEventOfType("solved");
		assertThat(solved.path("avatarIds").valueStream().map(JsonNode::asString).toList()).containsExactly(memberId);
		assertThat(solved.path("solve").path("nickname").asString()).isEqualTo(user.getNickname());
		assertThat(solved.path("solve").path("problemTitle").asString()).isEqualTo("짝수는 몇 개일까");
		int solvedTodayCount = solved.path("solvedTodayCount").asInt();
		assertThat(solvedTodayCount).as("오늘 푼 회원 수에 방금 푼 회원이 들어간다").isPositive();

		LoungeClient latecomer = connect(null);
		latecomer.join(200, 150, null);
		assertThat(latecomer.snapshot.path("recentSolves").get(0).path("nickname").asString()).isEqualTo(user.getNickname());
		assertThat(latecomer.snapshot.path("solvedTodayCount").asInt()).isGreaterThanOrEqualTo(solvedTodayCount);
	}

	@Test
	@DisplayName("관전: 주인 고양이의 아바타 id 로 들어와도 실제 방 id(무작위)를 받고, 주인이 보낸 문서 전체·변경이 관전자에게만 간다. 큰 코드(200KB)도 한 번에 간다")
	void relaysOwnerDocumentToSpectators() throws Exception {
		LoungeClient owner = connect(null);
		String ownerId = owner.join(100, 120, 7);
		String roomId = owner.host();
		assertThat(roomId).startsWith("d-").isNotEqualTo(ownerId);
		LoungeClient spectator = connect(null);
		String spectatorId = spectator.join(160, 130, null);
		LoungeClient outsider = connect(null);
		outsider.join(200, 150, null);

		spectator.send("/app/desk/" + ownerId + "/join", Map.of());
		JsonNode opened = spectator.awaitDesk("opened");
		assertThat(opened.path("roomId").asString()).isEqualTo(roomId);
		assertThat(opened.path("target").asString()).isEqualTo(ownerId);
		assertThat(opened.path("problemId").asLong()).isEqualTo(1);
		assertThat(opened.path("language").asString()).isEqualTo("JAVA");
		assertThat(opened.path("pairable").asBoolean()).isFalse();
		assertThat(owner.awaitDesk("member-joined").path("memberId").asString()).isEqualTo(spectatorId);
		assertThat(owner.awaitDesk("spectators").path("spectatorCount").asInt()).isEqualTo(1);

		String bigDocument = "A".repeat(200_000);
		owner.send("/app/desk/" + roomId + "/full", Map.of("to", spectatorId, "kind", "doc", "payload", bigDocument));
		assertThat(spectator.awaitDesk("full").path("payload").asString()).hasSize(200_000);

		owner.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "AQID"));
		assertThat(spectator.awaitDesk("update").path("payload").asString()).isEqualTo("AQID");
		outsider.assertNoDesk("update");
	}

	@Test
	@DisplayName("관전: 주인이 아직 풀이 화면을 열지 않은 책상에 들어가면 not-found 로 기다리다가, 주인이 방을 열면 opened 를 받고 주인은 문서를 보내 달라는 요청을 받는다")
	void spectatorWaitsUntilOwnerHosts() throws Exception {
		LoungeClient owner = connect(null);
		String ownerId = owner.join(100, 120, 7);
		LoungeClient spectator = connect(null);
		String spectatorId = spectator.join(160, 130, null);

		spectator.send("/app/desk/" + ownerId + "/join", Map.of());
		spectator.awaitDesk("not-found");

		String roomId = owner.host();
		JsonNode opened = spectator.awaitDesk("opened");
		assertThat(opened.path("roomId").asString()).isEqualTo(roomId);
		assertThat(opened.path("target").asString()).as("처음 보낸 target 을 그대로 실어야 관전 화면이 자기 답으로 안다").isEqualTo(ownerId);
		assertThat(owner.awaitDesk("member-joined").path("memberId").asString()).isEqualTo(spectatorId);
	}

	@Test
	@DisplayName("관전: 주인이 풀이 화면을 닫으면 paused, 다시 열면 같은 관전자가 새 방의 opened 를 받는다. 관전 화면을 나간 사람은 받지 않는다")
	void spectatorFollowsOwnerAfterClose() throws Exception {
		LoungeClient owner = connect(null);
		owner.join(100, 120, 7);
		String firstRoom = owner.host();
		LoungeClient stayed = connect(null);
		stayed.join(160, 130, null);
		LoungeClient gone = connect(null);
		gone.join(200, 150, null);
		stayed.send("/app/desk/" + firstRoom + "/join", Map.of());
		gone.send("/app/desk/" + firstRoom + "/join", Map.of());
		stayed.awaitDesk("opened");
		gone.awaitDesk("opened");

		owner.send("/app/desk/close", Map.of());
		stayed.awaitDesk("paused");
		gone.awaitDesk("paused");
		gone.send("/app/desk/" + firstRoom + "/leave", Map.of());
		// 나가기에는 답이 없고, 다른 연결의 메시지는 서로 다른 스레드에서 처리된다. 나가기가 먼저 처리되도록 잠깐 둔다
		Thread.sleep(300);

		String secondRoom = owner.host();
		JsonNode reopened = stayed.awaitDesk("opened");
		assertThat(secondRoom).isNotEqualTo(firstRoom);
		assertThat(reopened.path("roomId").asString()).isEqualTo(secondRoom);
		assertThat(reopened.path("target").asString()).isEqualTo(firstRoom);
		gone.assertNoDesk("opened");
	}

	@Test
	@DisplayName("관전자가 보낸 문서 변경은 서버가 버린다 (읽기 전용 강제). 게스트 주인의 연결이 끊기면 기다리지 않고 closed, 닫힌 방은 not-found")
	void enforcesReadOnlyAndClosesRoom() throws Exception {
		LoungeClient owner = connect(null);
		owner.join(100, 120, 7);
		String roomId = owner.host();
		LoungeClient spectator = connect(null);
		spectator.join(160, 130, null);
		LoungeClient another = connect(null);
		another.join(200, 150, null);
		spectator.send("/app/desk/" + roomId + "/join", Map.of());
		another.send("/app/desk/" + roomId + "/join", Map.of());
		spectator.awaitDesk("opened");
		another.awaitDesk("opened");

		spectator.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "AQID"));
		another.assertNoDesk("update");

		owner.disconnect();
		spectator.awaitDesk("closed");
		spectator.send("/app/desk/" + roomId + "/join", Map.of());
		spectator.awaitDesk("not-found");
	}

	@Test
	@DisplayName("페어: 로그인한 관전자의 요청을 회원 주인이 수락하면, 상대의 변경이 주인·다른 관전자에게 가고 1:1 채팅은 두 사람만 받는다")
	void pairPartnerCanWriteAndChat() throws Exception {
		LoungeClient owner = connectMember("pair-owner");
		owner.join(100, 120, 7);
		String roomId = owner.host();
		User user = newUser("pair-partner");
		LoungeClient partner = connect(cookieOf(user));
		String partnerId = partner.join(160, 130, null);
		LoungeClient spectator = connect(null);
		spectator.join(200, 150, null);
		partner.send("/app/desk/" + roomId + "/join", Map.of());
		spectator.send("/app/desk/" + roomId + "/join", Map.of());
		assertThat(partner.awaitDesk("opened").path("pairable").asBoolean()).isTrue();
		spectator.awaitDesk("opened");

		partner.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "AQID"));
		owner.assertNoDesk("update");

		partner.send("/app/desk/" + roomId + "/pair-request", Map.of());
		JsonNode requested = owner.awaitDesk("pair-requested");
		assertThat(requested.path("memberId").asString()).isEqualTo(partnerId);
		assertThat(requested.path("nickname").asString()).isEqualTo(user.getNickname());
		owner.send("/app/desk/pair-accept", Map.of("memberId", partnerId));
		partner.awaitDesk("pair-started");
		assertThat(owner.awaitDesk("pair-started").path("nickname").asString()).isEqualTo(user.getNickname());

		partner.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "BAUG"));
		assertThat(owner.awaitDesk("update").path("payload").asString()).isEqualTo("BAUG");
		assertThat(spectator.awaitDesk("update").path("payload").asString()).isEqualTo("BAUG");

		partner.send("/app/desk/" + roomId + "/chat", Map.of("text", "5번째 줄 봐 주세요"));
		assertThat(owner.awaitDesk("chat").path("text").asString()).isEqualTo("5번째 줄 봐 주세요");
		spectator.assertNoDesk("chat");

		owner.send("/app/desk/" + roomId + "/pair-end", Map.of());
		partner.awaitDesk("pair-ended");
		partner.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "BwgJ"));
		spectator.assertNoDesk("update");
	}

	@Test
	@DisplayName("페어는 회원끼리만: 게스트의 요청도, 게스트 주인의 방에 보낸 회원의 요청도 주인에게 가지 않는다")
	void pairNeedsMembersOnBothSides() throws Exception {
		LoungeClient memberOwner = connectMember("pair-member-owner");
		memberOwner.join(100, 120, 7);
		String memberRoom = memberOwner.host();
		LoungeClient guest = connect(null);
		guest.join(160, 130, null);
		guest.send("/app/desk/" + memberRoom + "/join", Map.of());
		guest.awaitDesk("opened");
		guest.send("/app/desk/" + memberRoom + "/pair-request", Map.of());
		memberOwner.assertNoDesk("pair-requested");

		LoungeClient guestOwner = connect(null);
		guestOwner.join(200, 150, 8);
		String guestRoom = guestOwner.host();
		LoungeClient member = connectMember("pair-member-spectator");
		member.join(220, 150, null);
		member.send("/app/desk/" + guestRoom + "/join", Map.of());
		assertThat(member.awaitDesk("opened").path("pairable").asBoolean()).isFalse();
		member.send("/app/desk/" + guestRoom + "/pair-request", Map.of());
		guestOwner.assertNoDesk("pair-requested");
	}

	@Test
	@DisplayName("주인 본인이 다른 탭으로 자기 방을 관전하면 페어를 요청할 수 없다 (pairable=false, 요청도 버림). 게스트는 로그인하면 되므로 true")
	void ownerCannotPairWithThemselves() throws Exception {
		User ownerUser = newUser("self-pair");
		LoungeClient owner = connect(cookieOf(ownerUser));
		owner.join(100, 120, 7);
		String roomId = owner.host();

		LoungeClient ownerOtherTab = connect(cookieOf(ownerUser));
		ownerOtherTab.join(160, 130, null);
		ownerOtherTab.send("/app/desk/" + roomId + "/join", Map.of());
		assertThat(ownerOtherTab.awaitDesk("opened").path("pairable").asBoolean()).isFalse();
		ownerOtherTab.send("/app/desk/" + roomId + "/pair-request", Map.of());
		owner.assertNoDesk("pair-requested");

		LoungeClient guest = connect(null);
		guest.join(200, 150, null);
		guest.send("/app/desk/" + roomId + "/join", Map.of());
		assertThat(guest.awaitDesk("opened").path("pairable").asBoolean()).isTrue();
	}

	@Test
	@DisplayName("회원 주인이 끊겼다가 유예 안에 다시 연결되면 같은 방 id 로 이어 열고, 관전자는 새 문서를 받는다. 그동안 페어 상대는 글을 못 쓴다")
	void memberOwnerResumesWithinGrace() throws Exception {
		User ownerUser = newUser("resume-owner");
		LoungeClient owner = connect(cookieOf(ownerUser));
		owner.join(100, 120, 7);
		String roomId = owner.host();
		LoungeClient partner = connectMember("resume-owner-partner");
		String partnerId = partner.join(160, 130, null);
		partner.send("/app/desk/" + roomId + "/join", Map.of());
		partner.awaitDesk("opened");
		pair(owner, partner, roomId, partnerId);

		owner.disconnect();
		partner.awaitDesk("owner-away");
		LoungeClient spectator = connect(null);
		spectator.join(200, 150, null);
		spectator.send("/app/desk/" + roomId + "/join", Map.of());
		spectator.awaitDesk("opened");
		spectator.awaitDesk("owner-away");
		partner.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "AQID"));
		spectator.assertNoDesk("update");

		LoungeClient returned = connect(cookieOf(ownerUser));
		returned.join(100, 120, 7);
		assertThat(returned.host()).isEqualTo(roomId);
		partner.awaitDesk("reset");
		spectator.awaitDesk("reset");
		assertThat(returned.awaitDesk("pair-started").path("memberId").asString()).isEqualTo(partnerId);
		partner.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "BAUG"));
		assertThat(returned.awaitDesk("update").path("payload").asString()).isEqualTo("BAUG");
	}

	@Test
	@DisplayName("페어 상대가 끊겼다가 유예 안에 새 연결로 다시 들어오면 페어가 이어지고 다시 쓸 수 있다")
	void partnerResumesWithinGrace() throws Exception {
		LoungeClient owner = connectMember("resume-partner-owner");
		owner.join(100, 120, 7);
		String roomId = owner.host();
		User partnerUser = newUser("resume-partner");
		LoungeClient partner = connect(cookieOf(partnerUser));
		String partnerId = partner.join(160, 130, null);
		partner.send("/app/desk/" + roomId + "/join", Map.of());
		partner.awaitDesk("opened");
		pair(owner, partner, roomId, partnerId);

		partner.disconnect();
		owner.awaitDesk("pair-away");

		LoungeClient returned = connect(cookieOf(partnerUser));
		String returnedId = returned.join(160, 130, null);
		returned.send("/app/desk/" + roomId + "/join", Map.of());
		returned.awaitDesk("opened");
		returned.awaitDesk("pair-started");
		assertThat(owner.awaitDesk("pair-started").path("memberId").asString()).isEqualTo(returnedId);
		returned.send("/app/desk/" + roomId + "/update", Map.of("kind", "doc", "payload", "BAUG"));
		assertThat(owner.awaitDesk("update").path("payload").asString()).isEqualTo("BAUG");
	}

	@Test
	@DisplayName("유예 안에 돌아오지 않으면 페어 상대는 페어가 끝나고, 회원 주인의 방은 닫힌다")
	void graceExpires() throws Exception {
		LoungeClient owner = connectMember("expire-owner");
		owner.join(100, 120, 7);
		String roomId = owner.host();
		LoungeClient partner = connectMember("expire-partner");
		String partnerId = partner.join(160, 130, null);
		partner.send("/app/desk/" + roomId + "/join", Map.of());
		partner.awaitDesk("opened");
		pair(owner, partner, roomId, partnerId);
		LoungeClient spectator = connect(null);
		spectator.join(200, 150, null);
		spectator.send("/app/desk/" + roomId + "/join", Map.of());
		spectator.awaitDesk("opened");

		partner.disconnect();
		owner.awaitDesk("pair-away");
		owner.awaitDesk("pair-ended");

		owner.disconnect();
		spectator.awaitDesk("owner-away");
		spectator.awaitDesk("closed");
	}

	@Test
	@DisplayName("전체 채팅: 게스트도 말할 수 있고 모두에게 방송된다. 200자 초과·1초 안 연속은 거절, 나중에 들어온 사람은 최근 채팅을 받는다")
	void loungeChatWithLimits() throws Exception {
		LoungeClient speaker = connect(null);
		String speakerId = speaker.join(100, 120, null);
		LoungeClient listener = connect(null);
		listener.join(200, 150, null);

		speaker.send("/app/lounge/chat", Map.of("text", "  안녕하세요  "));
		JsonNode chat = listener.awaitEventOfType("chat");
		assertThat(chat.path("chat").path("avatarId").asString()).isEqualTo(speakerId);
		assertThat(chat.path("chat").path("text").asString()).isEqualTo("안녕하세요");

		speaker.send("/app/lounge/chat", Map.of("text", "바로 또"));
		assertThat(speaker.awaitPersonal("chat-rejected").path("reason").asString()).isEqualTo("too-fast");

		Thread.sleep(1100);
		speaker.send("/app/lounge/chat", Map.of("text", "가".repeat(201)));
		assertThat(speaker.awaitPersonal("chat-rejected").path("reason").asString()).isEqualTo("too-long");

		LoungeClient latecomer = connect(null);
		latecomer.join(160, 130, null);
		assertThat(latecomer.snapshot.path("recentChats").valueStream().map(item -> item.path("text").asString()).toList())
				.contains("안녕하세요");
	}

	@Test
	@DisplayName("같은 회원이 다른 탭으로 들어오면 이전 탭의 고양이는 퇴장하고 그 탭에 replaced 가 간다. 라운지에는 한 마리만 남는다")
	void memberHasOneCatAcrossTabs() throws Exception {
		LoungeClient observer = connect(null);
		observer.join(200, 150, null);
		User user = newUser("one-cat-member");
		LoungeClient firstTab = connect(cookieOf(user));
		String firstId = firstTab.join(100, 120, 4);

		LoungeClient secondTab = connect(cookieOf(user));
		String secondId = secondTab.join(160, 130, null);

		firstTab.awaitPersonal("replaced");
		observer.awaitEvent("left", firstId);
		assertThat(observer.awaitEvent("joined", secondId).path("avatar").path("nickname").asString()).isEqualTo(user.getNickname());
		assertThat(secondTab.lastSnapshotIds()).contains(secondId).doesNotContain(firstId);
		observer.send("/app/lounge/sit", Map.of("deskId", 4));
		assertThat(observer.awaitEvent("sat", observer.myAvatarId).path("avatar").path("deskId").asInt())
				.as("빠진 고양이가 앉아 있던 책상은 비워진다").isEqualTo(4);
	}

	@Test
	@DisplayName("게스트는 같은 browserKey 면 이전 탭이 빠지고, 다른 browserKey(다른 브라우저·시크릿 창)면 따로 한 마리씩 있다")
	void guestHasOneCatPerBrowser() throws Exception {
		String browserKey = UUID.randomUUID().toString();
		LoungeClient firstTab = connect(null);
		String firstId = firstTab.joinFrom(browserKey);
		LoungeClient otherBrowser = connect(null);
		String otherId = otherBrowser.joinFrom(UUID.randomUUID().toString());

		LoungeClient secondTab = connect(null);
		String secondId = secondTab.joinFrom(browserKey);

		firstTab.awaitPersonal("replaced");
		otherBrowser.awaitEvent("left", firstId);
		assertThat(secondTab.lastSnapshotIds()).contains(secondId, otherId).doesNotContain(firstId);
	}

	@Test
	@DisplayName("허용한 프론트 주소가 아닌 다른 사이트(Origin)에서는 연결할 수 없다")
	void rejectsOtherOrigins() {
		assertThatThrownBy(() -> connect(null, "http://evil.example")).isInstanceOf(Exception.class);
	}

	private User newUser(String prefix) {
		return userService.findOrCreate(prefix + "-" + UUID.randomUUID(), prefix + "@example.com");
	}

	private String cookieOf(User user) {
		return "accessToken=" + tokenService.issue(user.getId());
	}

	private LoungeClient connectMember(String prefix) throws Exception {
		return connect(cookieOf(newUser(prefix)));
	}

	private static void pair(LoungeClient owner, LoungeClient partner, String roomId, String partnerId) throws InterruptedException {
		partner.send("/app/desk/" + roomId + "/pair-request", Map.of());
		owner.awaitDesk("pair-requested");
		owner.send("/app/desk/pair-accept", Map.of("memberId", partnerId));
		partner.awaitDesk("pair-started");
		owner.awaitDesk("pair-started");
	}

	private LoungeClient connect(String cookie) throws Exception {
		return connect(cookie, FRONTEND_ORIGIN);
	}

	private LoungeClient connect(String cookie, String origin) throws Exception {
		// 브라우저와 달리 테스트용 클라이언트는 받는 메시지 크기 제한(STOMP 64KB, WebSocket 버퍼 8KB)이 따로 있어 관전 테스트의 큰 문서를 위해 올린다
		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		container.setDefaultMaxTextMessageBufferSize(1024 * 1024);
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient(container));
		stompClient.setInboundMessageSizeLimit(1024 * 1024);
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
		private final BlockingQueue<JsonNode> desk = new LinkedBlockingQueue<>();
		private JsonNode snapshot;
		private String myAvatarId;

		LoungeClient(StompSession session) {
			this.session = session;
			session.subscribe("/topic/lounge", collector(events));
			session.subscribe("/user/queue/lounge", collector(personal));
			session.subscribe("/user/queue/desk", collector(desk));
		}

		String join(int x, int y, Integer deskId) throws InterruptedException {
			Map<String, Object> request = new HashMap<>(Map.of("coat", "grey", "x", x, "y", y, "direction", "down"));
			request.put("deskId", deskId);
			send("/app/lounge/join", request);
			return awaitSnapshot().path("myAvatarId").asString();
		}

		String joinFrom(String browserKey) throws InterruptedException {
			send("/app/lounge/join", Map.of("coat", "grey", "x", 100, "y", 120, "direction", "down", "browserKey", browserKey));
			return awaitSnapshot().path("myAvatarId").asString();
		}

		// 풀이 화면을 연 것처럼 방을 열고, 서버가 정한 방 id 를 돌려준다
		String host() throws InterruptedException {
			send("/app/desk/host", Map.of("problemId", 1, "language", "JAVA"));
			return awaitDesk("hosted").path("roomId").asString();
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

		JsonNode awaitEventOfType(String type) throws InterruptedException {
			return awaitMatching(events, message -> type.equals(message.path("type").asString()), type);
		}

		JsonNode awaitDesk(String type) throws InterruptedException {
			return awaitMatching(desk, message -> type.equals(message.path("type").asString()), "desk " + type);
		}

		// 잠깐 기다려도 이 종류의 책상 메시지가 오지 않아야 한다 (버려졌거나 받을 자격이 없음)
		void assertNoDesk(String type) throws InterruptedException {
			long deadline = System.nanoTime() + Duration.ofMillis(800).toNanos();
			while (System.nanoTime() < deadline) {
				JsonNode message = desk.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				assertThat(message == null ? null : message.path("type").asString()).isNotEqualTo(type);
			}
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
