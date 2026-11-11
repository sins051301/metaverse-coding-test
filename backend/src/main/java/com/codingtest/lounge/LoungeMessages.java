package com.codingtest.lounge;

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
	 * @param deskId 앉아 있으면 책상 번호, 서 있으면 null
	 */
	record AvatarState(String id, String nickname, boolean member, String coat,
			int x, int y, String direction, boolean moving, Integer deskId) {

		AvatarState movedTo(int x, int y, String direction, boolean moving) {
			return new AvatarState(id, nickname, member, coat, x, y, direction, moving, deskId);
		}

		AvatarState seatedAt(Integer deskId) {
			return new AvatarState(id, nickname, member, coat, x, y, direction, false, deskId);
		}

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

	// 서버 → 클라이언트

	/**
	 * /topic/lounge 로 모두에게 방송한다.
	 *
	 * @param type joined, moved, sat, stood, left
	 */
	record LoungeEvent(String type, AvatarState avatar) {
	}

	/**
	 * /user/queue/lounge 로 나에게만 보낸다.
	 *
	 * @param type       snapshot = 입장 직후 지금 있는 모두, sit-rejected = 그 책상에 이미 누가 앉아 있음
	 * @param myAvatarId snapshot 에서 내 아바타가 어느 것인지
	 * @param avatars    snapshot 의 전체 목록 (나 포함)
	 * @param deskId     sit-rejected 의 책상 번호
	 */
	record PersonalMessage(String type, String myAvatarId, List<AvatarState> avatars, Integer deskId) {

		static PersonalMessage snapshot(String myAvatarId, List<AvatarState> avatars) {
			return new PersonalMessage("snapshot", myAvatarId, avatars, null);
		}

		static PersonalMessage sitRejected(int deskId) {
			return new PersonalMessage("sit-rejected", null, null, deskId);
		}

	}

}
