package com.codingtest.lounge;

import java.time.Instant;

/**
 * 책상(코드 관전·페어) 중계 메시지 모양. 프론트의 frontend/src/entities/desk/api/deskMessages.ts 와 같아야 한다.
 *
 * <p>payload 는 Yjs 가 만든 바이트를 base64 로 바꾼 문자열이다. 서버는 내용을 풀어 보지 않고 그대로 전달한다 (단순 중계).
 */
final class DeskMessages {

	static final int PAIR_CHAT_MAX_LENGTH = 500;

	private DeskMessages() {
	}

	// 클라이언트 → 서버 (/app/desk/...)

	/**
	 * 주인이 풀이 화면을 열 때(문제·언어를 바꿀 때도) 보낸다. 방을 열거나, 이미 있으면 새 문서로 다시 연다.
	 */
	record HostRequest(long problemId, String language) {
	}

	/**
	 * @param kind doc = 문서 변경, awareness = 커서·선택 영역·이름
	 */
	record UpdateRequest(String kind, String payload) {
	}

	/**
	 * 새로 들어온 관전자 한 명에게 지금 문서 전체를 보낸다.
	 *
	 * @param to 받을 관전자의 아바타 id
	 */
	record FullRequest(String to, String kind, String payload) {
	}

	// 주인이 페어 요청을 수락·거절할 때 누구의 요청인지
	record PairAnswer(String memberId) {
	}

	record PairChatRequest(String text) {
	}

	// 서버 → 클라이언트 (/user/queue/desk)

	/**
	 * 나에게만 오는 책상 메시지. type 에 따라 쓰는 칸이 다르다.
	 *
	 * <ul>
	 *   <li>hosted (주인): 방을 열었다. roomId 가 관전 주소에 쓰이는 방 id 다</li>
	 *   <li>opened (관전자): 방에 들어왔다. target(요청할 때 쓴 id), problemId, language, nickname(주인), pairable(페어가 되는 방)</li>
	 *   <li>reset (관전자): 주인이 문제·언어를 바꿨거나 다시 연결돼 새 문서가 됐다. 지금 문서를 버리고 full 을 기다린다</li>
	 *   <li>owner-away (관전자): 주인 연결이 끊겨 돌아오길 기다리는 중. 그동안은 아무도 글을 못 쓴다</li>
	 *   <li>update, full: Yjs 바이트. kind, payload</li>
	 *   <li>closed (관전자): 주인이 일어났거나 나갔다 / not-found (관전자): 열린 방이 없다</li>
	 *   <li>member-joined (주인): 새 관전자. memberId 에게 full 을 보내라는 뜻 / spectators (주인): 관전자 수</li>
	 *   <li>pair-requested (주인): memberId, nickname 이 같이 풀자고 한다</li>
	 *   <li>pair-started (주인·상대): memberId(상대), nickname(상대) 와 페어 시작. 상대는 이제 글을 쓸 수 있다</li>
	 *   <li>pair-away (주인): 페어 상대 연결이 끊겨 돌아오길 기다리는 중</li>
	 *   <li>pair-declined (요청한 사람) / pair-ended (주인·상대)</li>
	 *   <li>chat (주인·상대): memberId(보낸 사람), nickname, text, sentAt</li>
	 * </ul>
	 */
	record DeskMessage(String type, String roomId, Long problemId, String language, String nickname,
			String kind, String payload, String memberId, Integer spectatorCount, String text, Instant sentAt, String target,
			Boolean pairable) {

		private static DeskMessage of(String type, String roomId) {
			return new DeskMessage(type, roomId, null, null, null, null, null, null, null, null, null, null, null);
		}

		static DeskMessage hosted(DeskRooms.Room room) {
			return new DeskMessage("hosted", room.id(), room.problemId(), room.language(), null,
					null, null, null, null, null, null, null, room.pairable());
		}

		static DeskMessage opened(DeskRooms.Room room, String target) {
			return new DeskMessage("opened", room.id(), room.problemId(), room.language(), room.ownerNickname(),
					null, null, null, null, null, null, target, room.pairable());
		}

		static DeskMessage reset(DeskRooms.Room room) {
			return new DeskMessage("reset", room.id(), room.problemId(), room.language(), room.ownerNickname(),
					null, null, null, null, null, null, null, room.pairable());
		}

		static DeskMessage bytes(String type, String roomId, String kind, String payload) {
			return new DeskMessage(type, roomId, null, null, null, kind, payload, null, null, null, null, null, null);
		}

		static DeskMessage closed(String roomId) {
			return of("closed", roomId);
		}

		static DeskMessage notFound(String roomId) {
			return of("not-found", roomId);
		}

		static DeskMessage memberJoined(String roomId, String memberId) {
			return new DeskMessage("member-joined", roomId, null, null, null, null, null, memberId, null, null, null, null, null);
		}

		static DeskMessage spectators(String roomId, int count) {
			return new DeskMessage("spectators", roomId, null, null, null, null, null, null, count, null, null, null, null);
		}

		static DeskMessage pair(String type, String roomId, String memberId, String nickname) {
			return new DeskMessage(type, roomId, null, null, nickname, null, null, memberId, null, null, null, null, null);
		}

		static DeskMessage ownerAway(String roomId) {
			return of("owner-away", roomId);
		}

		static DeskMessage pairAway(String roomId) {
			return of("pair-away", roomId);
		}

		static DeskMessage pairDeclined(String roomId) {
			return of("pair-declined", roomId);
		}

		static DeskMessage pairEnded(String roomId) {
			return of("pair-ended", roomId);
		}

		static DeskMessage chat(String roomId, String memberId, String nickname, String text, Instant sentAt) {
			return new DeskMessage("chat", roomId, null, null, nickname, null, null, memberId, null, text, sentAt, null, null);
		}

	}

}
