package com.codingtest.lounge;

import java.time.Instant;
import java.util.List;

/**
 * 라운지 STOMP 메시지 모양. 클라이언트가 보내는 요청과 서버가 보내는 알림을 한곳에 모았다.
 * 프론트의 frontend/src/entities/lounge/api/loungeMessages.ts 와 같아야 한다.
 */
final class LoungeMessages {

	private LoungeMessages() {
	}

	/**
	 * 다른 사람에게 보이는 아바타 하나의 상태.
	 *
	 * @param deskId   앉아 있으면 책상 번호, 서 있으면 null
	 * @param activity idle(그냥 있음), typing(마지막 입력 후 5초 안), napping(앉은 채 5분 입력 없음).
	 *                 판단은 클라이언트가 하고 바뀔 때만 알린다. 서 있으면 늘 idle
	 */
	record AvatarState(String id, String nickname, boolean member, String coat,
			int x, int y, String direction, boolean moving, Integer deskId, String activity) {

		AvatarState movedTo(int x, int y, String direction, boolean moving) {
			return new AvatarState(id, nickname, member, coat, x, y, direction, moving, deskId, activity);
		}

		AvatarState seatedAt(Integer deskId) {
			return new AvatarState(id, nickname, member, coat, x, y, direction, false, deskId, deskId == null ? "idle" : activity);
		}

		AvatarState withActivity(String activity) {
			return new AvatarState(id, nickname, member, coat, x, y, direction, moving, deskId, activity);
		}

	}

	/**
	 * 누군가 제출에서 AC 를 받은 기록. 라운지 오른쪽 "방금 푼 고양이" 목록과 벽 전광판에 쓴다.
	 */
	record RecentSolve(String nickname, String problemTitle, Instant solvedAt) {
	}

	/**
	 * 라운지 전체 채팅 한 줄. 게스트도 쓸 수 있다. 저장하지 않고 서버 메모리에 최근 것만 둔다.
	 */
	record ChatMessage(String avatarId, String nickname, String text, Instant sentAt) {
	}

	// 클라이언트 → 서버 (/app/lounge/...)

	/**
	 * 연결 직후(다시 연결했을 때도) 보낸다. 로그인하러 떠났다 오면 저장해 둔 자리·털색으로 다시 입장해 이어진 것처럼 보인다.
	 *
	 * @param nickname 게스트만 쓴다 (익명_XXXX). 회원은 서버가 DB 닉네임을 쓴다
	 * @param deskId   앉아 있던 채로 다시 연결했으면 그 책상. 그새 남이 앉았으면 서 있는 상태로 입장한다
	 */
	record JoinRequest(String nickname, String coat, int x, int y, String direction, Integer deskId) {
	}

	record MoveRequest(int x, int y, String direction, boolean moving) {
	}

	record SitRequest(int deskId) {
	}

	record ActivityRequest(String activity) {
	}

	record ChatRequest(String text) {
	}

	// 서버 → 클라이언트

	/**
	 * /topic/lounge 로 모두에게 방송한다.
	 *
	 * @param type joined, moved, sat, stood, left, activity
	 */
	record LoungeEvent(String type, AvatarState avatar) {
	}

	/**
	 * /topic/lounge 로 모두에게 방송한다. 누가 문제를 풀었는지.
	 *
	 * @param avatarIds 푼 회원의 아바타들 (탭을 여러 개 열었으면 여럿, 라운지에 없으면 비어 있음). 머리 위에 별을 띄운다
	 */
	record ChatEvent(String type, ChatMessage chat) {

		ChatEvent(ChatMessage chat) {
			this("chat", chat);
		}

	}

	record SolvedEvent(String type, List<String> avatarIds, RecentSolve solve) {

		SolvedEvent(List<String> avatarIds, RecentSolve solve) {
			this("solved", avatarIds, solve);
		}

	}

	/**
	 * /user/queue/lounge 로 나에게만 보낸다.
	 *
	 * @param type       snapshot = 입장 직후 지금 있는 모두, sit-rejected = 그 책상에 이미 누가 앉아 있음
	 * @param myAvatarId snapshot 에서 내 아바타가 어느 것인지
	 * @param avatars    snapshot 의 전체 목록 (나 포함)
	 * @param deskId       sit-rejected 의 책상 번호
	 * @param recentSolves snapshot 의 최근 푼 기록 (새것이 먼저)
	 * @param recentChats  snapshot 의 최근 채팅 (오래된 것이 먼저)
	 * @param reason       chat-rejected 의 이유 (too-long, too-fast)
	 */
	record PersonalMessage(String type, String myAvatarId, List<AvatarState> avatars, Integer deskId,
			List<RecentSolve> recentSolves, List<ChatMessage> recentChats, String reason) {

		static PersonalMessage snapshot(String myAvatarId, List<AvatarState> avatars, List<RecentSolve> recentSolves,
				List<ChatMessage> recentChats) {
			return new PersonalMessage("snapshot", myAvatarId, avatars, null, recentSolves, recentChats, null);
		}

		static PersonalMessage sitRejected(int deskId) {
			return new PersonalMessage("sit-rejected", null, null, deskId, null, null, null);
		}

		static PersonalMessage chatRejected(String reason) {
			return new PersonalMessage("chat-rejected", null, null, null, null, null, reason);
		}

	}

}
