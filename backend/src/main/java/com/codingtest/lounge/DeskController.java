package com.codingtest.lounge;

import com.codingtest.lounge.DeskMessages.DeskMessage;
import com.codingtest.lounge.DeskMessages.FullRequest;
import com.codingtest.lounge.DeskMessages.HostRequest;
import com.codingtest.lounge.DeskMessages.UpdateRequest;
import com.codingtest.lounge.LoungeMessages.AvatarState;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;

/**
 * 코드 관전 중계. Yjs 가 만든 바이트를 방 사람들에게 넘기기만 하고, 코드를 합치거나 저장하지 않는다 (README 10장 "관전").
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
		String nickname = loungeRegistry.find(identity.avatarId()).map(AvatarState::nickname).orElse("익명");
		rooms.host(identity.avatarId(), nickname, request.problemId(), request.language())
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
		rooms.leave(roomId, identity.avatarId())
				.ifPresent(room -> send(room.ownerId(), DeskMessage.spectators(roomId, room.spectators().size())));
	}

	// 주인의 문서 변경·커서. 주인이 아닌 사람이 보낸 것은 버린다 (화면의 읽기 전용만으로는 개발자 도구 우회를 못 막는다)
	@MessageMapping("/desk/{roomId}/update")
	void update(@DestinationVariable String roomId, UpdateRequest request, LoungeIdentity identity) {
		rooms.ownedBy(roomId, identity.avatarId()).ifPresent(room ->
				sendToAll(room.spectators(), DeskMessage.bytes("update", roomId, request.kind(), request.payload())));
	}

	@MessageMapping("/desk/{roomId}/full")
	void full(@DestinationVariable String roomId, FullRequest request, LoungeIdentity identity) {
		rooms.ownedBy(roomId, identity.avatarId())
				.filter(room -> room.spectators().contains(request.to()))
				.ifPresent(room -> send(request.to(), DeskMessage.bytes("full", roomId, request.kind(), request.payload())));
	}

	@EventListener
	void onDisconnect(SessionDisconnectEvent event) {
		if (event.getUser() instanceof LoungeIdentity identity) {
			closeRoom(identity.avatarId());
			rooms.leaveAll(identity.avatarId())
					.forEach(room -> send(room.ownerId(), DeskMessage.spectators(room.ownerId(), room.spectators().size())));
		}
	}

	private void closeRoom(String ownerId) {
		rooms.close(ownerId).ifPresent(room -> sendToAll(room.spectators(), DeskMessage.closed(ownerId)));
	}

	private void sendToAll(Set<String> avatarIds, DeskMessage message) {
		avatarIds.forEach(avatarId -> send(avatarId, message));
	}

	private void send(String avatarId, DeskMessage message) {
		messagingTemplate.convertAndSendToUser(avatarId, DESK_QUEUE, message);
	}

}
