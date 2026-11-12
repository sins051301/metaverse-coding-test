package com.codingtest.lounge;

import com.codingtest.lounge.DeskMessages.DeskMessage;
import com.codingtest.lounge.DeskMessages.FullRequest;
import com.codingtest.lounge.DeskMessages.HostRequest;
import com.codingtest.lounge.DeskMessages.PairAnswer;
import com.codingtest.lounge.DeskMessages.PairChatRequest;
import com.codingtest.lounge.DeskMessages.UpdateRequest;
import com.codingtest.lounge.DeskRooms.Away;
import com.codingtest.lounge.DeskRooms.Disconnected;
import com.codingtest.lounge.DeskRooms.Room;
import com.codingtest.lounge.LoungeMessages.AvatarState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 코드 관전·페어 중계. Yjs 가 만든 바이트를 방 사람들에게 넘기기만 하고, 코드를 합치거나 저장하지 않는다 (README 10장 "관전", "페어").
 *
 * <p>방송(/topic) 대신 한 명씩 개인 큐(/user/queue/desk)로 보낸다. /topic/desk/7 은 아무나 구독할 수 있어 "방에 들어온 사람만 받기"를
 * 지킬 수 없지만, 개인 큐는 서버가 방 명단을 보고 골라 보낸다. 방 인원이 몇 명뿐이라 한 명씩 보내도 부담이 없다.
 *
 * <p>회원 주인·페어 상대의 연결이 끊기면 reconnectGrace 동안 기다린다. 와이파이가 잠깐 끊겨 다시 연결되는 정도로 관전과 페어가 끝나지 않게 한다.
 */
@Controller
class DeskController {

	static final String DESK_QUEUE = "/queue/desk";

	private final DeskRooms rooms;
	private final LoungeRegistry loungeRegistry;
	private final SimpMessagingTemplate messagingTemplate;
	private final TaskScheduler graceScheduler;
	private final Duration reconnectGrace;

	DeskController(DeskRooms rooms, LoungeRegistry loungeRegistry, SimpMessagingTemplate messagingTemplate,
			@Qualifier("deskGraceScheduler") TaskScheduler graceScheduler,
			@Value("${lounge.desk.reconnect-grace}") Duration reconnectGrace) {
		this.rooms = rooms;
		this.loungeRegistry = loungeRegistry;
		this.messagingTemplate = messagingTemplate;
		this.graceScheduler = graceScheduler;
		this.reconnectGrace = reconnectGrace;
	}

	// 방을 연다. 같은 회원의 방이 이미 있으면(다시 연결함) 이어 열고, 관전자들에게 새 문서를 받으라고 알린다
	@MessageMapping("/desk/host")
	void host(HostRequest request, LoungeIdentity identity) {
		DeskRooms.Hosted hosted = rooms.host(identity, nicknameOf(identity), request.problemId(), request.language());
		Room room = hosted.room();
		String owner = identity.avatarId();
		send(owner, DeskMessage.hosted(room));
		if (!hosted.reopened()) {
			return;
		}
		sendToAll(room.spectators(), DeskMessage.reset(room));
		room.spectators().forEach(spectator -> send(owner, DeskMessage.memberJoined(room.id(), spectator)));
		send(owner, DeskMessage.spectators(room.id(), room.spectators().size()));
		if (room.partnerNickname() != null) {
			send(owner, DeskMessage.pair("pair-started", room.id(), room.partnerAvatarId(), room.partnerNickname()));
			if (room.partnerAvatarId() == null) {
				send(owner, DeskMessage.pairAway(room.id()));
			}
		}
	}

	@MessageMapping("/desk/close")
	void close(LoungeIdentity identity) {
		rooms.close(identity.avatarId()).ifPresent(this::announceClosed);
	}

	/**
	 * 관전 시작: 관전자에게 방 정보를, 주인에게 "이 사람에게 문서 전체를 보내 달라"와 관전자 수를 보낸다.
	 * 페어 상대였던 회원이 다시 들어오면 페어를 이어 간다.
	 *
	 * @param target 방 id, 또는 주인의 아바타 id (라운지에서 들어올 때). 답(opened)의 roomId 가 실제 방 id 다
	 */
	@MessageMapping("/desk/{target}/join")
	void join(@DestinationVariable String target, LoungeIdentity identity) {
		String me = identity.avatarId();
		rooms.join(target, identity).ifPresentOrElse(joined -> {
			Room room = joined.room();
			send(me, DeskMessage.opened(room, target));
			if (room.ownerAway()) {
				send(me, DeskMessage.ownerAway(room.id()));
			} else {
				send(room.ownerAvatarId(), DeskMessage.memberJoined(room.id(), me));
				send(room.ownerAvatarId(), DeskMessage.spectators(room.id(), room.spectators().size()));
			}
			if (joined.resumedPartner()) {
				send(me, DeskMessage.pair("pair-started", room.id(), me, room.ownerNickname()));
				if (!room.ownerAway()) {
					send(room.ownerAvatarId(), DeskMessage.pair("pair-started", room.id(), me, room.partnerNickname()));
				}
				if (joined.replacedAvatarId() != null) {
					send(joined.replacedAvatarId(), DeskMessage.pairEnded(room.id()));
				}
			}
		}, () -> send(me, DeskMessage.notFound(target)));
	}

	@MessageMapping("/desk/{roomId}/leave")
	void leave(@DestinationVariable String roomId, LoungeIdentity identity) {
		rooms.leave(roomId, identity.avatarId()).ifPresent(left -> {
			Room room = left.room();
			if (left.endedPair()) {
				send(identity.avatarId(), DeskMessage.pairEnded(roomId));
				sendToOwner(room, DeskMessage.pairEnded(roomId));
			}
			sendToOwner(room, DeskMessage.spectators(roomId, room.spectators().size()));
		});
	}

	// 주인·페어 상대의 문서 변경·커서. 관전자가 보낸 것은 버린다 (화면의 읽기 전용만으로는 개발자 도구 우회를 못 막는다)
	@MessageMapping("/desk/{roomId}/update")
	void update(@DestinationVariable String roomId, UpdateRequest request, LoungeIdentity identity) {
		rooms.writableBy(roomId, identity.avatarId()).ifPresent(room -> {
			Set<String> recipients = room.members();
			recipients.remove(identity.avatarId());
			sendToAll(recipients, DeskMessage.bytes("update", roomId, request.kind(), request.payload()));
		});
	}

	@MessageMapping("/desk/{roomId}/full")
	void full(@DestinationVariable String roomId, FullRequest request, LoungeIdentity identity) {
		rooms.ownedBy(roomId, identity.avatarId())
				.filter(room -> room.spectators().contains(request.to()))
				.ifPresent(room -> send(request.to(), DeskMessage.bytes("full", roomId, request.kind(), request.payload())));
	}

	// 페어는 회원끼리만 (FR-01-2 지연 로그인). 게스트가 보낸 요청, 게스트 주인의 방에 보낸 요청은 버린다
	@MessageMapping("/desk/{roomId}/pair-request")
	void requestPair(@DestinationVariable String roomId, LoungeIdentity identity) {
		rooms.requestPair(roomId, identity, nicknameOf(identity)).ifPresent(room ->
				send(room.ownerAvatarId(), DeskMessage.pair("pair-requested", roomId, identity.avatarId(), nicknameOf(identity))));
	}

	@MessageMapping("/desk/pair-accept")
	void acceptPair(PairAnswer answer, LoungeIdentity identity) {
		rooms.acceptPair(identity.avatarId(), answer.memberId()).ifPresent(room -> {
			send(room.ownerAvatarId(), DeskMessage.pair("pair-started", room.id(), answer.memberId(), room.partnerNickname()));
			send(answer.memberId(), DeskMessage.pair("pair-started", room.id(), answer.memberId(), room.ownerNickname()));
		});
	}

	@MessageMapping("/desk/pair-decline")
	void declinePair(PairAnswer answer, LoungeIdentity identity) {
		rooms.declinePair(identity.avatarId(), answer.memberId())
				.ifPresent(room -> send(answer.memberId(), DeskMessage.pairDeclined(room.id())));
	}

	@MessageMapping("/desk/{roomId}/pair-end")
	void endPair(@DestinationVariable String roomId, LoungeIdentity identity) {
		rooms.endPair(roomId, identity.avatarId()).ifPresent(ended -> {
			sendIfPresent(ended.ownerAvatarId(), DeskMessage.pairEnded(roomId));
			sendIfPresent(ended.partnerAvatarId(), DeskMessage.pairEnded(roomId));
		});
	}

	// 페어 두 사람만 받는다. 저장하지 않는다
	@MessageMapping("/desk/{roomId}/chat")
	void chat(@DestinationVariable String roomId, PairChatRequest request, LoungeIdentity identity) {
		String text = request.text() == null ? "" : request.text().strip();
		if (text.isEmpty() || text.length() > DeskMessages.PAIR_CHAT_MAX_LENGTH) {
			return;
		}
		rooms.pairOf(roomId, identity.avatarId()).ifPresent(pair -> sendToAll(pair,
				DeskMessage.chat(roomId, identity.avatarId(), nicknameOf(identity), text, Instant.now())));
	}

	@EventListener
	void onDisconnect(SessionDisconnectEvent event) {
		if (!(event.getUser() instanceof LoungeIdentity identity)) {
			return;
		}
		Disconnected disconnected = rooms.disconnect(identity.avatarId());
		disconnected.closed().forEach(this::announceClosed);
		disconnected.ownersAway().forEach(this::waitForOwner);
		disconnected.partnersAway().forEach(this::waitForPartner);
		disconnected.left().forEach(room -> sendToOwner(room, DeskMessage.spectators(room.id(), room.spectators().size())));
	}

	private void waitForOwner(Away away) {
		Room room = away.room();
		sendToAll(room.spectators(), DeskMessage.ownerAway(room.id()));
		afterGrace(() -> rooms.expireOwner(room.id(), away.ticket()).ifPresent(this::announceClosed));
	}

	private void waitForPartner(Away away) {
		Room room = away.room();
		sendToOwner(room, DeskMessage.pairAway(room.id()));
		sendToOwner(room, DeskMessage.spectators(room.id(), room.spectators().size()));
		afterGrace(() -> rooms.expirePartner(room.id(), away.ticket())
				.ifPresent(expired -> sendToOwner(expired, DeskMessage.pairEnded(expired.id()))));
	}

	private void afterGrace(Runnable task) {
		try {
			graceScheduler.schedule(task, Instant.now().plus(reconnectGrace));
		} catch (TaskRejectedException exception) {
			// 서버가 꺼지는 중이라 시계가 멈췄다. 모든 연결이 끊기며 들어온 정리 요청이고, 방은 메모리와 함께 사라지므로 할 일이 없다
		}
	}

	private void announceClosed(Room room) {
		sendToAll(room.spectators(), DeskMessage.closed(room.id()));
	}

	private String nicknameOf(LoungeIdentity identity) {
		if (identity.member()) {
			return identity.nickname();
		}
		return loungeRegistry.find(identity.avatarId()).map(AvatarState::nickname).orElse("익명");
	}

	private void sendToOwner(Room room, DeskMessage message) {
		sendIfPresent(room.ownerAvatarId(), message);
	}

	private void sendIfPresent(String avatarId, DeskMessage message) {
		if (avatarId != null) {
			send(avatarId, message);
		}
	}

	private void sendToAll(Set<String> avatarIds, DeskMessage message) {
		avatarIds.forEach(avatarId -> send(avatarId, message));
	}

	private void send(String avatarId, DeskMessage message) {
		messagingTemplate.convertAndSendToUser(avatarId, DESK_QUEUE, message);
	}

}
