package com.codingtest.lounge;

import com.codingtest.lounge.DeskMessages.DeskMessage;
import com.codingtest.lounge.DeskMessages.FullRequest;
import com.codingtest.lounge.DeskMessages.HostRequest;
import com.codingtest.lounge.DeskMessages.PairAnswer;
import com.codingtest.lounge.DeskMessages.PairChatRequest;
import com.codingtest.lounge.DeskMessages.UpdateRequest;
import com.codingtest.lounge.LoungeMessages.AvatarState;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Set;

/**
 * 코드 관전·페어 중계. Yjs 가 만든 바이트를 방 사람들에게 넘기기만 하고, 코드를 합치거나 저장하지 않는다 (README 10장 "관전", "페어").
 *
 * <p>방송(/topic) 대신 한 명씩 개인 큐(/user/queue/desk)로 보낸다. /topic/desk/7 은 아무나 구독할 수 있어 "방에 들어온 사람만 받기"를
 * 지킬 수 없지만, 개인 큐는 서버가 방 명단을 보고 골라 보낸다. 방 인원이 몇 명뿐이라 한 명씩 보내도 부담이 없다.
 */
@Controller
class DeskController {

	static final String DESK_QUEUE = "/queue/desk";

	private final DeskRooms rooms;
	private final LoungeRegistry loungeRegistry;
	private final SimpMessagingTemplate messagingTemplate;

	DeskController(DeskRooms rooms, LoungeRegistry loungeRegistry, SimpMessagingTemplate messagingTemplate) {
		this.rooms = rooms;
		this.loungeRegistry = loungeRegistry;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/desk/host")
	void host(HostRequest request, LoungeIdentity identity) {
		rooms.host(identity.avatarId(), nicknameOf(identity.avatarId()), request.problemId(), request.language())
				.ifPresent(room -> sendToAll(room.spectators(), DeskMessage.reset(room)));
	}

	@MessageMapping("/desk/close")
	void close(LoungeIdentity identity) {
		closeRoom(identity.avatarId());
	}

	// 관전 시작: 관전자에게 방 정보를, 주인에게 "이 사람에게 문서 전체를 보내 달라"와 관전자 수를 보낸다
	@MessageMapping("/desk/{roomId}/join")
	void join(@DestinationVariable String roomId, LoungeIdentity identity) {
		rooms.join(roomId, identity.avatarId()).ifPresentOrElse(room -> {
			send(identity.avatarId(), DeskMessage.opened(room));
			send(room.ownerId(), DeskMessage.memberJoined(roomId, identity.avatarId()));
			send(room.ownerId(), DeskMessage.spectators(roomId, room.spectators().size()));
		}, () -> send(identity.avatarId(), DeskMessage.notFound(roomId)));
	}

	@MessageMapping("/desk/{roomId}/leave")
	void leave(@DestinationVariable String roomId, LoungeIdentity identity) {
		boolean wasPartner = rooms.partnerOf(roomId).filter(identity.avatarId()::equals).isPresent();
		rooms.leave(roomId, identity.avatarId()).ifPresent(room -> {
			if (wasPartner) {
				endPair(roomId, identity.avatarId());
			}
			send(room.ownerId(), DeskMessage.spectators(roomId, room.spectators().size()));
		});
	}

	// 주인·페어 상대의 문서 변경·커서. 관전자가 보낸 것은 버린다 (화면의 읽기 전용만으로는 개발자 도구 우회를 못 막는다)
	@MessageMapping("/desk/{roomId}/update")
	void update(@DestinationVariable String roomId, UpdateRequest request, LoungeIdentity identity) {
		rooms.writableBy(roomId, identity.avatarId()).ifPresent(room -> {
			Set<String> recipients = rooms.members(room);
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

	// 페어 요청은 로그인한 사람만 (FR-01-2 지연 로그인). 게스트가 보낸 요청은 버린다
	@MessageMapping("/desk/{roomId}/pair-request")
	void requestPair(@DestinationVariable String roomId, LoungeIdentity identity) {
		if (!identity.member()) {
			return;
		}
		rooms.requestPair(roomId, identity.avatarId()).ifPresent(room ->
				send(room.ownerId(), DeskMessage.pair("pair-requested", roomId, identity.avatarId(), nicknameOf(identity.avatarId()))));
	}

	@MessageMapping("/desk/pair-accept")
	void acceptPair(PairAnswer answer, LoungeIdentity identity) {
		String roomId = identity.avatarId();
		rooms.acceptPair(roomId, answer.memberId()).ifPresent(room -> {
			DeskMessage started = DeskMessage.pair("pair-started", roomId, answer.memberId(), nicknameOf(answer.memberId()));
			send(room.ownerId(), started);
			send(answer.memberId(), DeskMessage.pair("pair-started", roomId, answer.memberId(), room.ownerNickname()));
		});
	}

	@MessageMapping("/desk/pair-decline")
	void declinePair(PairAnswer answer, LoungeIdentity identity) {
		if (rooms.declinePair(identity.avatarId(), answer.memberId())) {
			send(answer.memberId(), DeskMessage.pairDeclined(identity.avatarId()));
		}
	}

	@MessageMapping("/desk/{roomId}/pair-end")
	void endPairRequest(@DestinationVariable String roomId, LoungeIdentity identity) {
		endPair(roomId, identity.avatarId());
	}

	// 페어 두 사람만 받는다. 저장하지 않는다
	@MessageMapping("/desk/{roomId}/chat")
	void chat(@DestinationVariable String roomId, PairChatRequest request, LoungeIdentity identity) {
		String text = request.text() == null ? "" : request.text().strip();
		if (text.isEmpty() || text.length() > DeskMessages.PAIR_CHAT_MAX_LENGTH) {
			return;
		}
		rooms.pairOf(roomId, identity.avatarId()).ifPresent(pair -> sendToAll(pair,
				DeskMessage.chat(roomId, identity.avatarId(), nicknameOf(identity.avatarId()), text, Instant.now())));
	}

	@EventListener
	void onDisconnect(SessionDisconnectEvent event) {
		if (event.getUser() instanceof LoungeIdentity identity) {
			String avatarId = identity.avatarId();
			closeRoom(avatarId);
			rooms.leaveAll(avatarId).forEach(room -> {
				if (rooms.partnerOf(room.ownerId()).filter(avatarId::equals).isPresent()) {
					endPair(room.ownerId(), avatarId);
				}
				send(room.ownerId(), DeskMessage.spectators(room.ownerId(), room.spectators().size()));
			});
		}
	}

	private void endPair(String roomId, String actorId) {
		rooms.endPair(roomId, actorId).ifPresent(partner -> {
			send(roomId, DeskMessage.pairEnded(roomId));
			send(partner, DeskMessage.pairEnded(roomId));
		});
	}

	private void closeRoom(String ownerId) {
		rooms.close(ownerId).ifPresent(room -> sendToAll(room.spectators(), DeskMessage.closed(ownerId)));
	}

	private String nicknameOf(String avatarId) {
		return loungeRegistry.find(avatarId).map(AvatarState::nickname).orElse("익명");
	}

	private void sendToAll(Set<String> avatarIds, DeskMessage message) {
		avatarIds.forEach(avatarId -> send(avatarId, message));
	}

	private void send(String avatarId, DeskMessage message) {
		messagingTemplate.convertAndSendToUser(avatarId, DESK_QUEUE, message);
	}

}
