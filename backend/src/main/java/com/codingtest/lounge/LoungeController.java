package com.codingtest.lounge;

import com.codingtest.lounge.LoungeMessages.AvatarState;
import com.codingtest.lounge.LoungeMessages.JoinRequest;
import com.codingtest.lounge.LoungeMessages.LoungeEvent;
import com.codingtest.lounge.LoungeMessages.MoveRequest;
import com.codingtest.lounge.LoungeMessages.PersonalMessage;
import com.codingtest.lounge.LoungeMessages.SitRequest;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 라운지 STOMP 메시지를 받아 접속자 목록(LoungeRegistry)을 고치고, 바뀐 것을 알린다.
 *
 * <p>@MessageMapping("/lounge/move") = 클라이언트가 /app/lounge/move 로 보낸 메시지를 받는 메서드. REST 의 @PostMapping 과 같은 역할이다.
 * LoungeIdentity 파라미터에는 Spring 이 이 연결의 신원(핸드셰이크 때 정한 Principal)을 넣어 준다. 메시지 본문에는 누가 보냈는지가 없다.
 */
@Controller
class LoungeController {

	static final String TOPIC = "/topic/lounge";
	static final String PERSONAL_QUEUE = "/queue/lounge";

	private final LoungeRegistry registry;
	private final SimpMessagingTemplate messagingTemplate;

	LoungeController(LoungeRegistry registry, SimpMessagingTemplate messagingTemplate) {
		this.registry = registry;
		this.messagingTemplate = messagingTemplate;
	}

	// 입장: 나에게는 지금 있는 모두(snapshot)를, 다른 사람에게는 "내가 들어왔다"를 보낸다
	@MessageMapping("/lounge/join")
	void join(JoinRequest request, LoungeIdentity identity) {
		AvatarState avatar = registry.join(identity, request);
		messagingTemplate.convertAndSendToUser(identity.getName(), PERSONAL_QUEUE,
				PersonalMessage.snapshot(avatar.id(), registry.all()));
		broadcast("joined", avatar);
	}

	@MessageMapping("/lounge/move")
	void move(MoveRequest request, LoungeIdentity identity) {
		registry.move(identity.avatarId(), request).ifPresent(avatar -> broadcast("moved", avatar));
	}

	@MessageMapping("/lounge/sit")
	void sit(SitRequest request, LoungeIdentity identity) {
		registry.sit(identity.avatarId(), request.deskId()).ifPresentOrElse(
				avatar -> broadcast("sat", avatar),
				() -> messagingTemplate.convertAndSendToUser(identity.getName(), PERSONAL_QUEUE,
						PersonalMessage.sitRejected(request.deskId())));
	}

	@MessageMapping("/lounge/stand")
	void stand(LoungeIdentity identity) {
		registry.stand(identity.avatarId()).ifPresent(avatar -> broadcast("stood", avatar));
	}

	// 탭을 닫거나 네트워크가 끊기면 연결이 끝난다. 그 아바타를 지우고 앉아 있던 책상을 비운다
	@EventListener
	void onDisconnect(SessionDisconnectEvent event) {
		if (event.getUser() instanceof LoungeIdentity identity) {
			registry.leave(identity.avatarId()).ifPresent(avatar -> broadcast("left", avatar));
		}
	}

	private void broadcast(String type, AvatarState avatar) {
		messagingTemplate.convertAndSend(TOPIC, new LoungeEvent(type, avatar));
	}

}
