package com.codingtest.lounge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 열린 책상 방들. 서버 메모리에만 두고 문서 내용은 갖지 않는다 (주인 브라우저가 늘 최신 문서를 가짐).
 *
 * <p>방 id 는 방을 처음 열 때 만드는 무작위 값(d-xxxxxxxx)이고, 방이 닫히면 버린다. 그래서 예전 관전 링크는 다시 쓸 수 없다.
 * 회원 주인은 연결이 바뀌어도(재연결, 새로고침) 같은 방을 이어 쓴다. 아바타 id 는 연결마다 새로 생기므로 주인은 회원 id 로 알아본다.
 *
 * <p>방 하나에 주인 1명 + 관전자 여럿 + 그중 페어 상대 최대 1명. 페어는 회원 주인과 회원 관전자 사이에서만 된다.
 * 회원 주인·페어 상대의 연결이 끊기면 바로 정리하지 않고 "잠깐 자리 비움"으로 두었다가, 유예 시간 안에 돌아오지 않으면 정리한다 (expire...).
 *
 * <p>주인이 아직 풀이 화면을 열지 않았거나 풀이 화면을 닫은 책상에 들어온 관전자는 "대기"로 둔다. 주인이 방을 열면 대기자를 관전자로 넣는다.
 * 그래서 관전 화면을 다시 열지 않아도 주인이 풀기 시작하는 순간 코드가 보인다. 대기는 주인의 지금 연결(아바타 id)에 묶고, 주인이 라운지를 떠나면 버린다.
 *
 * <p>모든 메서드는 synchronized 다. 방 몇 개·몇 명 규모라 잠금 하나로 충분하고, 여러 칸(관전자·요청·상대)을 한 번에 바꿔도 어긋나지 않는다.
 */
@Component
class DeskRooms {

	/**
	 * 바깥에 넘기는 방의 복사본.
	 *
	 * @param ownerAvatarId   주인의 지금 연결. 연결이 끊겨 돌아오길 기다리는 동안은 null
	 * @param pairable        주인이 회원이라 페어가 되는 방인지
	 * @param partnerAvatarId 페어 상대의 지금 연결. 상대가 없거나 돌아오길 기다리는 동안은 null
	 */
	record Room(String id, String ownerAvatarId, String ownerNickname, boolean pairable, long problemId, String language,
			Set<String> spectators, String partnerAvatarId, String partnerNickname) {

		boolean ownerAway() {
			return ownerAvatarId == null;
		}

		// 메시지를 받을 사람 전부 (주인 + 관전자, 페어 상대도 관전자 명단에 들어 있다)
		Set<String> members() {
			Set<String> members = new LinkedHashSet<>(spectators);
			if (ownerAvatarId != null) {
				members.add(ownerAvatarId);
			}
			return members;
		}

	}

	/**
	 * @param spectator 기다리는 관전자
	 * @param target    관전자가 들어올 때 쓴 값 (주인의 아바타 id, 또는 닫히기 전의 방 id). opened 답에 그대로 실어 관전자가 자기 답인지 알아보게 한다
	 */
	record Waiter(LoungeIdentity spectator, String target) {
	}

	/**
	 * @param reopened        이미 있던 방을 이어 연 경우 (문제·언어를 바꿈, 다시 연결함, 다른 탭에서 엶)
	 * @param waiters         이 주인이 방을 열기를 기다리던 관전자들. 관전자로 넣어 opened 를 보낸다
	 */
	record Hosted(Room room, boolean reopened, List<Waiter> waiters) {
	}

	/**
	 * @param pairable       이 관전자가 페어를 요청할 수 있는 방인지 (회원 주인의 방이고, 관전자가 주인 본인이 아님). 게스트도 로그인하면 되므로 true
	 * @param resumedPartner 페어 상대가 다시 들어와 페어를 이어 간다
	 * @param replacedAvatarId 상대의 예전 연결 (다른 탭에서 들어왔거나 끊긴 줄 모르는 연결). 페어가 끝났다고 알린다
	 */
	record Joined(Room room, boolean pairable, boolean resumedPartner, String replacedAvatarId) {
	}

	record Left(Room room, boolean endedPair) {
	}

	// 페어가 끝났을 때 알릴 두 연결. 자리를 비운 쪽은 null
	record EndedPair(String ownerAvatarId, String partnerAvatarId) {
	}

	// 연결이 끊겨 돌아오길 기다리기 시작한 방. ticket 이 그대로일 때만 유예가 끝나 정리한다 (그사이 돌아왔다 다시 끊긴 경우 구분)
	record Away(Room room, long ticket) {
	}

	record Disconnected(List<Room> closed, List<Away> ownersAway, List<Away> partnersAway, List<Room> left) {
	}

	private record Requester(long userId, String nickname) {
	}

	private static final class State {

		final String id;
		final String ownerKey;
		final Long ownerUserId;
		String ownerAvatarId;
		String ownerNickname;
		long problemId;
		String language;
		long ownerAwayTicket;
		final Map<String, LoungeIdentity> spectators = new LinkedHashMap<>();
		final Map<String, Requester> pairRequests = new LinkedHashMap<>();
		Long partnerUserId;
		String partnerAvatarId;
		String partnerNickname;
		long partnerAwayTicket;

		State(String id, LoungeIdentity owner) {
			this.id = id;
			this.ownerKey = ownerKey(owner);
			this.ownerUserId = owner.userId();
		}

		boolean pairable() {
			return ownerUserId != null;
		}

		void endPair() {
			partnerUserId = null;
			partnerAvatarId = null;
			partnerNickname = null;
			partnerAwayTicket = 0;
		}

		Room snapshot() {
			return new Room(id, ownerAvatarId, ownerNickname, pairable(), problemId, language, Set.copyOf(spectators.keySet()),
					partnerAvatarId, partnerNickname);
		}

	}

	private static final String ROOM_ID_PREFIX = "d-";

	private final Map<String, State> rooms = new HashMap<>();
	private final Map<String, String> roomIdByOwnerKey = new HashMap<>();
	// 주인 아바타 id → (관전자 아바타 id → 대기자)
	private final Map<String, Map<String, Waiter>> waitingByOwnerAvatar = new HashMap<>();
	private long lastTicket;

	/**
	 * 방을 연다. 같은 주인(회원이면 같은 회원 id)의 방이 이미 있으면 새로 만들지 않고 이 연결을 주인으로 삼아 이어 쓴다.
	 * 페어 상대와 관전자는 그대로 두고, 답을 기다리던 페어 요청은 버린다.
	 */
	synchronized Hosted host(LoungeIdentity owner, String ownerNickname, long problemId, String language) {
		State state = Optional.ofNullable(roomIdByOwnerKey.get(ownerKey(owner))).map(rooms::get).orElse(null);
		boolean reopened = state != null;
		if (state == null) {
			state = new State(ROOM_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8), owner);
			rooms.put(state.id, state);
			roomIdByOwnerKey.put(state.ownerKey, state.id);
		}
		state.ownerAvatarId = owner.avatarId();
		state.ownerNickname = ownerNickname;
		state.problemId = problemId;
		state.language = language;
		state.ownerAwayTicket = 0;
		state.pairRequests.clear();
		Map<String, Waiter> waiting = waitingByOwnerAvatar.remove(owner.avatarId());
		List<Waiter> waiters = waiting == null ? List.of() : List.copyOf(waiting.values());
		return new Hosted(state.snapshot(), reopened, waiters);
	}

	/**
	 * @param target 방 id, 또는 주인의 아바타 id (라운지에서 책상 주인 고양이를 보고 들어올 때).
	 *               주인의 아바타 id 인데 아직 방이 없으면 대기로 둔다 (비어 있음을 돌려주지만, 주인이 방을 열면 host 의 waiters 로 나온다)
	 */
	synchronized Optional<Joined> join(String target, LoungeIdentity spectator) {
		stopWaiting(spectator.avatarId());
		State state = find(target);
		if (state == null) {
			if (!target.startsWith(ROOM_ID_PREFIX) && !target.equals(spectator.avatarId())) {
				startWaiting(target, new Waiter(spectator, target));
			}
			return Optional.empty();
		}
		if (spectator.avatarId().equals(state.ownerAvatarId)) {
			return Optional.empty();
		}
		state.spectators.put(spectator.avatarId(), spectator);
		boolean resumedPartner = spectator.member() && spectator.userId().equals(state.partnerUserId);
		String replaced = null;
		if (resumedPartner) {
			replaced = state.partnerAvatarId;
			if (replaced != null && !replaced.equals(spectator.avatarId())) {
				state.spectators.remove(replaced);
			} else {
				replaced = null;
			}
			state.partnerAvatarId = spectator.avatarId();
			state.partnerAwayTicket = 0;
		}
		boolean pairable = state.pairable() && !(spectator.member() && spectator.userId().equals(state.ownerUserId));
		return Optional.of(new Joined(state.snapshot(), pairable, resumedPartner, replaced));
	}

	// 관전 화면을 닫고 나감. 페어 상대였으면 기다리지 않고 페어를 끝낸다 (스스로 나간 것이므로)
	synchronized Optional<Left> leave(String roomId, String spectatorId) {
		stopWaiting(spectatorId);
		State state = rooms.get(roomId);
		if (state == null || state.spectators.remove(spectatorId) == null) {
			return Optional.empty();
		}
		state.pairRequests.remove(spectatorId);
		boolean endedPair = spectatorId.equals(state.partnerAvatarId);
		if (endedPair) {
			state.endPair();
		}
		return Optional.of(new Left(state.snapshot(), endedPair));
	}

	// 문서를 바꿀 수 있는 사람(주인, 페어 상대)이 보낸 것만 통과. 주인이 자리를 비운 동안은 아무도 못 쓴다 (돌아온 주인의 문서가 덮어쓰므로)
	synchronized Optional<Room> writableBy(String roomId, String senderId) {
		State state = rooms.get(roomId);
		if (state == null || state.ownerAvatarId == null) {
			return Optional.empty();
		}
		boolean writer = senderId.equals(state.ownerAvatarId) || senderId.equals(state.partnerAvatarId);
		return writer ? Optional.of(state.snapshot()) : Optional.empty();
	}

	synchronized Optional<Room> ownedBy(String roomId, String senderId) {
		State state = rooms.get(roomId);
		return state != null && senderId.equals(state.ownerAvatarId) ? Optional.of(state.snapshot()) : Optional.empty();
	}

	// 회원 주인의 방에서, 관전 중인 다른 회원만, 아직 페어 상대가 없을 때만 요청할 수 있다
	synchronized Optional<Room> requestPair(String roomId, LoungeIdentity requester, String nickname) {
		State state = rooms.get(roomId);
		if (state == null || state.ownerAvatarId == null || !state.pairable() || !requester.member()
				|| requester.userId().equals(state.ownerUserId) || !state.spectators.containsKey(requester.avatarId())
				|| state.partnerUserId != null) {
			return Optional.empty();
		}
		state.pairRequests.put(requester.avatarId(), new Requester(requester.userId(), nickname));
		return Optional.of(state.snapshot());
	}

	/**
	 * 주인이 수락. 상대가 이미 있으면 거절된다 (두 요청을 거의 동시에 수락해도 상대는 한 명).
	 *
	 * @return 상대가 됐으면 그 방. 요청한 적 없거나 이미 상대가 있으면 비어 있음
	 */
	synchronized Optional<Room> acceptPair(String ownerAvatarId, String requesterId) {
		State state = findByOwnerAvatar(ownerAvatarId);
		if (state == null) {
			return Optional.empty();
		}
		Requester requester = state.pairRequests.remove(requesterId);
		if (requester == null || !state.spectators.containsKey(requesterId) || state.partnerUserId != null) {
			return Optional.empty();
		}
		state.partnerUserId = requester.userId();
		state.partnerAvatarId = requesterId;
		state.partnerNickname = requester.nickname();
		return Optional.of(state.snapshot());
	}

	// @return 거절한 요청이 있던 방
	synchronized Optional<Room> declinePair(String ownerAvatarId, String requesterId) {
		State state = findByOwnerAvatar(ownerAvatarId);
		return state != null && state.pairRequests.remove(requesterId) != null ? Optional.of(state.snapshot()) : Optional.empty();
	}

	// 주인이나 상대가 페어를 끝낸다. 상대는 관전자로 남는다
	synchronized Optional<EndedPair> endPair(String roomId, String actorId) {
		State state = rooms.get(roomId);
		if (state == null || state.partnerUserId == null
				|| !(actorId.equals(state.ownerAvatarId) || actorId.equals(state.partnerAvatarId))) {
			return Optional.empty();
		}
		EndedPair ended = new EndedPair(state.ownerAvatarId, state.partnerAvatarId);
		state.endPair();
		return Optional.of(ended);
	}

	// 페어 채팅을 받을 연결들 (자리를 비운 쪽은 빠진다). 보낸 사람이 페어가 아니면 비어 있음
	synchronized Optional<Set<String>> pairOf(String roomId, String senderId) {
		State state = rooms.get(roomId);
		if (state == null || state.partnerAvatarId == null || state.ownerAvatarId == null
				|| !(senderId.equals(state.ownerAvatarId) || senderId.equals(state.partnerAvatarId))) {
			return Optional.empty();
		}
		return Optional.of(Set.of(state.ownerAvatarId, state.partnerAvatarId));
	}

	// 주인이 풀이 화면을 닫음 (일어나기, 다른 화면으로 이동). 기다리지 않고 바로 닫는다.
	// 주인은 아직 라운지에 있으니 관전자들은 대기로 옮긴다. 주인이 다시 풀기 시작하면 이어서 본다
	synchronized Optional<Room> close(String ownerAvatarId) {
		State state = findByOwnerAvatar(ownerAvatarId);
		if (state == null) {
			return Optional.empty();
		}
		state.spectators.values().forEach(spectator -> startWaiting(ownerAvatarId, new Waiter(spectator, state.id)));
		return Optional.of(remove(state));
	}

	/**
	 * 연결이 끊긴 사람을 정리한다.
	 *
	 * <ul>
	 *   <li>게스트 주인의 방: 바로 닫는다 (다시 연결해도 같은 사람인지 알 수 없다. 코드는 브라우저 draft 에 남아 있다)</li>
	 *   <li>회원 주인의 방: 자리 비움으로 두고 유예 후 expireOwner</li>
	 *   <li>페어 상대: 관전자 명단에서 빼고 페어는 자리 비움으로 두고 유예 후 expirePartner</li>
	 *   <li>그 밖의 관전자: 명단에서 뺀다 (주인에게 관전자 수를 알림)</li>
	 *   <li>대기: 이 사람이 기다리던 것도, 이 사람을 기다리던 대기자 목록도 버린다</li>
	 * </ul>
	 */
	synchronized Disconnected disconnect(String avatarId) {
		stopWaiting(avatarId);
		waitingByOwnerAvatar.remove(avatarId);
		List<Room> closed = new ArrayList<>();
		List<Away> ownersAway = new ArrayList<>();
		List<Away> partnersAway = new ArrayList<>();
		List<Room> left = new ArrayList<>();
		for (State state : List.copyOf(rooms.values())) {
			if (avatarId.equals(state.ownerAvatarId)) {
				if (state.pairable()) {
					state.ownerAvatarId = null;
					state.ownerAwayTicket = ++lastTicket;
					state.pairRequests.clear();
					ownersAway.add(new Away(state.snapshot(), state.ownerAwayTicket));
				} else {
					closed.add(remove(state));
				}
			} else if (state.spectators.remove(avatarId) != null) {
				state.pairRequests.remove(avatarId);
				if (avatarId.equals(state.partnerAvatarId)) {
					state.partnerAvatarId = null;
					state.partnerAwayTicket = ++lastTicket;
					partnersAway.add(new Away(state.snapshot(), state.partnerAwayTicket));
				} else {
					left.add(state.snapshot());
				}
			}
		}
		return new Disconnected(closed, ownersAway, partnersAway, left);
	}

	// 유예가 끝났는데 주인이 돌아오지 않았으면 방을 닫는다
	synchronized Optional<Room> expireOwner(String roomId, long ticket) {
		State state = rooms.get(roomId);
		if (state == null || state.ownerAvatarId != null || state.ownerAwayTicket != ticket) {
			return Optional.empty();
		}
		return Optional.of(remove(state));
	}

	// 유예가 끝났는데 상대가 돌아오지 않았으면 페어를 끝낸다
	synchronized Optional<Room> expirePartner(String roomId, long ticket) {
		State state = rooms.get(roomId);
		if (state == null || state.partnerUserId == null || state.partnerAvatarId != null
				|| state.partnerAwayTicket != ticket) {
			return Optional.empty();
		}
		state.endPair();
		return Optional.of(state.snapshot());
	}

	private void startWaiting(String ownerAvatarId, Waiter waiter) {
		stopWaiting(waiter.spectator().avatarId());
		waitingByOwnerAvatar.computeIfAbsent(ownerAvatarId, owner -> new LinkedHashMap<>())
				.put(waiter.spectator().avatarId(), waiter);
	}

	// 한 관전자는 한 책상만 기다린다
	private void stopWaiting(String spectatorAvatarId) {
		waitingByOwnerAvatar.values().removeIf(waiters -> {
			waiters.remove(spectatorAvatarId);
			return waiters.isEmpty();
		});
	}

	private State find(String target) {
		State state = rooms.get(target);
		return state != null ? state : findByOwnerAvatar(target);
	}

	private State findByOwnerAvatar(String ownerAvatarId) {
		return rooms.values().stream()
				.filter(state -> ownerAvatarId.equals(state.ownerAvatarId))
				.findFirst()
				.orElse(null);
	}

	private Room remove(State state) {
		rooms.remove(state.id);
		roomIdByOwnerKey.remove(state.ownerKey, state.id);
		return state.snapshot();
	}

	// 같은 주인인지 가리는 열쇠. 회원은 회원 id(연결이 바뀌어도 같음), 게스트는 연결마다 다른 아바타 id
	private static String ownerKey(LoungeIdentity owner) {
		return owner.member() ? "u-" + owner.userId() : owner.avatarId();
	}

}
