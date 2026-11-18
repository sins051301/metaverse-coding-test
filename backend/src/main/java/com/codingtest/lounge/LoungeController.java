package com.codingtest.lounge;

import com.codingtest.lounge.LoungeMessages.ActivityRequest;
import com.codingtest.lounge.LoungeMessages.AvatarState;
import com.codingtest.lounge.LoungeMessages.ChatEvent;
import com.codingtest.lounge.LoungeMessages.ChatRequest;
import com.codingtest.lounge.LoungeMessages.JoinRequest;
import com.codingtest.lounge.LoungeMessages.LoungeEvent;
import com.codingtest.lounge.LoungeMessages.MoveRequest;
import com.codingtest.lounge.LoungeMessages.PersonalMessage;
import com.codingtest.lounge.LoungeMessages.RecentSolve;
import com.codingtest.lounge.LoungeMessages.SitRequest;
import com.codingtest.lounge.LoungeMessages.SolvedEvent;
import com.codingtest.problem.ProblemService;
import com.codingtest.submission.SubmissionAcceptedEvent;
import com.codingtest.user.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;

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
	private final UserService userService;
	private final ProblemService problemService;
	private final TodaySolvers todaySolvers;

	LoungeController(LoungeRegistry registry, SimpMessagingTemplate messagingTemplate, UserService userService,
			ProblemService problemService, TodaySolvers todaySolvers) {
		this.registry = registry;
		this.messagingTemplate = messagingTemplate;
		this.userService = userService;
		this.problemService = problemService;
		this.todaySolvers = todaySolvers;
	}

	// 입장: 나에게는 지금 있는 모두(snapshot)를, 다른 사람에게는 "내가 들어왔다"를 보낸다.
	// 같은 사람이 다른 탭에 두고 있던 고양이는 빠지고, 그 탭에는 replaced 를 보내 다시 연결하지 않게 한다
	@MessageMapping("/lounge/join")
	void join(JoinRequest request, LoungeIdentity identity) {
		LoungeRegistry.Joined joined = registry.join(identity, request);
		AvatarState avatar = joined.avatar();
		joined.replaced().ifPresent(previous -> {
			messagingTemplate.convertAndSendToUser(previous.id(), PERSONAL_QUEUE, PersonalMessage.replaced());
			broadcast("left", previous);
		});
		messagingTemplate.convertAndSendToUser(identity.getName(), PERSONAL_QUEUE,
				PersonalMessage.snapshot(avatar.id(), registry.all(), registry.recentSolves(), registry.recentChats(),
						todaySolvers.count()));
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

	// 라운지 전체 채팅. 게스트도 쓸 수 있어 길이·빈도는 서버가 막는다. 거절하면 보낸 사람에게만 이유를 알린다
	@MessageMapping("/lounge/chat")
	void chat(ChatRequest request, LoungeIdentity identity) {
		switch (registry.chat(identity.avatarId(), request.text(), Instant.now())) {
			case LoungeRegistry.Accepted accepted -> messagingTemplate.convertAndSend(TOPIC, new ChatEvent(accepted.chat()));
			case LoungeRegistry.Rejected rejected -> messagingTemplate.convertAndSendToUser(identity.getName(), PERSONAL_QUEUE,
					PersonalMessage.chatRejected(rejected.reason()));
		}
	}

	// 타이핑·낮잠. 키 하나마다가 아니라 상태가 바뀔 때만 온다
	@MessageMapping("/lounge/activity")
	void activity(ActivityRequest request, LoungeIdentity identity) {
		registry.setActivity(identity.avatarId(), request.activity()).ifPresent(avatar -> broadcast("activity", avatar));
	}

	/**
	 * 누가 제출에서 AC 를 받았다 (SubmissionJudgeWorker 가 결과를 저장한 뒤 남김).
	 * "풀었다"는 클라이언트가 아니라 서버의 채점 결과로만 정한다. 클라이언트가 보내게 하면 누구나 거짓으로 보낼 수 있다.
	 * 채점 작업 스레드에서 불린다. 알림 쪽 오류가 채점 결과를 바꾸지는 않는다 (발행이 결과 저장 뒤라서).
	 */
	@EventListener
	void onSubmissionAccepted(SubmissionAcceptedEvent event) {
		String nickname = userService.findNickname(event.userId()).orElse(null);
		if (nickname == null) {
			return;
		}
		RecentSolve solve = new RecentSolve(nickname, problemService.getProblem(event.problemId()).getTitle(), Instant.now());
		registry.recordSolve(solve);
		int solvedTodayCount = todaySolvers.add(event.userId());
		messagingTemplate.convertAndSend(TOPIC, new SolvedEvent(registry.avatarIdsOf(event.userId()), solve, solvedTodayCount));
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
