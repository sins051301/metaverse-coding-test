package com.codingtest.lounge;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 열린 책상 방들. 방 id = 주인의 아바타 id. 서버 메모리에만 두고 문서 내용은 갖지 않는다 (주인 브라우저가 늘 최신 문서를 가짐).
 *
 * <p>방 하나에 주인 1명 + 관전자 여럿 + 그중 페어 상대 최대 1명. 글을 쓸 수 있는 사람은 주인과 페어 상대뿐이다.
 */
@Component
class DeskRooms {

	record Room(String ownerId, String ownerNickname, long problemId, String language, Set<String> spectators) {

		Room withProblem(long problemId, String language) {
			return new Room(ownerId, ownerNickname, problemId, language, spectators);
		}

	}

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();
	// 방 id → 페어 상대. 상대는 관전자 중 한 명이고, 주인이 수락했을 때만 생긴다
	private final Map<String, String> partners = new ConcurrentHashMap<>();
	// 방 id → 페어를 요청하고 주인의 답을 기다리는 사람들
	private final Map<String, Set<String>> pairRequests = new ConcurrentHashMap<>();

	/**
	 * 방을 연다. 이미 열려 있으면(문제·언어를 바꿈) 관전자와 페어 상대는 그대로 두고 문제만 바꾼다.
	 *
	 * @return 다시 연 경우 그 방 (관전자에게 reset 을 보내야 함), 처음 연 경우 비어 있음
	 */
	Optional<Room> host(String ownerId, String ownerNickname, long problemId, String language) {
		Room previous = rooms.get(ownerId);
		if (previous == null) {
			rooms.put(ownerId, new Room(ownerId, ownerNickname, problemId, language, ConcurrentHashMap.newKeySet()));
			return Optional.empty();
		}
		Room reopened = previous.withProblem(problemId, language);
		rooms.put(ownerId, reopened);
		return Optional.of(reopened);
	}

	Optional<Room> join(String roomId, String spectatorId) {
		Room room = rooms.get(roomId);
		if (room == null || room.ownerId().equals(spectatorId)) {
			return Optional.empty();
		}
		room.spectators().add(spectatorId);
		return Optional.of(room);
	}

	/**
	 * @return 빠진 방. 빠진 사람이 페어 상대였으면 페어도 끝난다 (endedPair 로 확인)
	 */
	Optional<Room> leave(String roomId, String spectatorId) {
		Room room = rooms.get(roomId);
		if (room == null || !room.spectators().remove(spectatorId)) {
			return Optional.empty();
		}
		requestsOf(roomId).remove(spectatorId);
		return Optional.of(room);
	}

	// 방 안의 모든 사람 (주인 + 관전자, 페어 상대도 관전자 명단에 들어 있다)
	Set<String> members(Room room) {
		Set<String> members = ConcurrentHashMap.newKeySet();
		members.add(room.ownerId());
		members.addAll(room.spectators());
		return members;
	}

	// 문서를 바꿀 수 있는 사람(주인, 페어 상대)이 보낸 것만 통과. 관전자·방 밖 사람이 보낸 것은 비어 있음 (읽기 전용 강제)
	Optional<Room> writableBy(String roomId, String senderId) {
		Room room = rooms.get(roomId);
		if (room == null) {
			return Optional.empty();
		}
		boolean writer = room.ownerId().equals(senderId) || senderId.equals(partners.get(roomId));
		return writer ? Optional.of(room) : Optional.empty();
	}

	Optional<Room> ownedBy(String roomId, String senderId) {
		Room room = rooms.get(roomId);
		return room != null && room.ownerId().equals(senderId) ? Optional.of(room) : Optional.empty();
	}

	Optional<String> partnerOf(String roomId) {
		return Optional.ofNullable(partners.get(roomId));
	}

	// 관전 중인 사람만, 아직 페어 상대가 없는 방에만 요청할 수 있다
	Optional<Room> requestPair(String roomId, String requesterId) {
		Room room = rooms.get(roomId);
		if (room == null || !room.spectators().contains(requesterId) || partners.containsKey(roomId)) {
			return Optional.empty();
		}
		requestsOf(roomId).add(requesterId);
		return Optional.of(room);
	}

	/**
	 * 주인이 수락. putIfAbsent 라서 두 요청을 거의 동시에 수락해도 상대는 한 명만 된다.
	 *
	 * @return 상대가 됐으면 그 방. 요청한 적 없거나 이미 상대가 있으면 비어 있음
	 */
	Optional<Room> acceptPair(String ownerId, String requesterId) {
		Room room = rooms.get(ownerId);
		if (room == null || !requestsOf(ownerId).remove(requesterId) || !room.spectators().contains(requesterId)) {
			return Optional.empty();
		}
		return partners.putIfAbsent(ownerId, requesterId) == null ? Optional.of(room) : Optional.empty();
	}

	boolean declinePair(String ownerId, String requesterId) {
		return rooms.containsKey(ownerId) && requestsOf(ownerId).remove(requesterId);
	}

	// 주인이나 상대가 페어를 끝낸다. 상대는 관전자로 남는다
	Optional<String> endPair(String roomId, String actorId) {
		Room room = rooms.get(roomId);
		String partner = partners.get(roomId);
		if (room == null || partner == null || !(room.ownerId().equals(actorId) || partner.equals(actorId))) {
			return Optional.empty();
		}
		return partners.remove(roomId, partner) ? Optional.of(partner) : Optional.empty();
	}

	// 페어 채팅을 보낼 수 있는 두 사람
	Optional<Set<String>> pairOf(String roomId, String senderId) {
		Room room = rooms.get(roomId);
		String partner = partners.get(roomId);
		if (room == null || partner == null || !(room.ownerId().equals(senderId) || partner.equals(senderId))) {
			return Optional.empty();
		}
		return Optional.of(Set.of(room.ownerId(), partner));
	}

	Optional<Room> close(String ownerId) {
		partners.remove(ownerId);
		pairRequests.remove(ownerId);
		return Optional.ofNullable(rooms.remove(ownerId));
	}

	// 연결이 끊긴 사람이 관전 중이던 방들에서 빠진다. 빠진 방들을 돌려줘 주인에게 관전자 수를 알린다
	List<Room> leaveAll(String spectatorId) {
		return rooms.values().stream()
				.filter(room -> room.spectators().remove(spectatorId))
				.peek(room -> requestsOf(room.ownerId()).remove(spectatorId))
				.toList();
	}

	private Set<String> requestsOf(String roomId) {
		return pairRequests.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet());
	}

}
