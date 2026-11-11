package com.codingtest.lounge;

/**
 * 책상(코드 관전) 중계 메시지 모양. 프론트의 frontend/src/entities/desk/api/deskMessages.ts 와 같아야 한다.
 *
 * <p>payload 는 Yjs 가 만든 바이트를 base64 로 바꾼 문자열이다. 서버는 내용을 풀어 보지 않고 그대로 전달한다 (단순 중계).
 */
final class DeskMessages {

	private DeskMessages() {
	}

	// 클라이언트 → 서버 (/app/desk/...)

	/**
	 * 주인이 풀이 화면을 열 때(문제·언어를 바꿀 때도) 보낸다. 방을 열거나, 이미 있으면 새 문서로 다시 연다.
	 */
	record HostRequest(long problemId, String language) {
	}

	/**
	 * @param kind doc = 문서 변경, awareness = 커서·선택 영역
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

	// 서버 → 클라이언트 (/user/queue/desk)

	/**
	 * 나에게만 오는 책상 메시지. type 에 따라 쓰는 칸이 다르다.
	 *
	 * <ul>
	 *   <li>opened (관전자): 방에 들어왔다. problemId, language, nickname(주인)</li>
	 *   <li>reset (관전자): 주인이 문제·언어를 바꿔 새 문서가 됐다. problemId, language. 지금 문서를 버리고 full 을 기다린다</li>
	 *   <li>update, full (관전자): Yjs 바이트. kind, payload</li>
	 *   <li>closed (관전자): 주인이 일어났거나 나갔다</li>
	 *   <li>not-found (관전자): 그 책상에 열린 방이 없다 (주인이 아직 풀이 화면을 열지 않음)</li>
	 *   <li>member-joined (주인): 새 관전자가 들어왔다. memberId 에게 full 을 보내라는 뜻</li>
	 *   <li>spectators (주인): 지금 관전자 수. spectatorCount</li>
	 * </ul>
	 */
	record DeskMessage(String type, String roomId, Long problemId, String language, String nickname,
			String kind, String payload, String memberId, Integer spectatorCount) {

		static DeskMessage opened(DeskRooms.Room room) {
			return new DeskMessage("opened", room.ownerId(), room.problemId(), room.language(), room.ownerNickname(), null, null, null, null);
		}

		static DeskMessage reset(DeskRooms.Room room) {
			return new DeskMessage("reset", room.ownerId(), room.problemId(), room.language(), room.ownerNickname(), null, null, null, null);
		}

		static DeskMessage bytes(String type, String roomId, String kind, String payload) {
			return new DeskMessage(type, roomId, null, null, null, kind, payload, null, null);
		}

		static DeskMessage closed(String roomId) {
			return new DeskMessage("closed", roomId, null, null, null, null, null, null, null);
		}

		static DeskMessage notFound(String roomId) {
			return new DeskMessage("not-found", roomId, null, null, null, null, null, null, null);
		}

		static DeskMessage memberJoined(String roomId, String memberId) {
			return new DeskMessage("member-joined", roomId, null, null, null, null, null, memberId, null);
		}

		static DeskMessage spectators(String roomId, int count) {
			return new DeskMessage("spectators", roomId, null, null, null, null, null, null, count);
		}

	}

}
