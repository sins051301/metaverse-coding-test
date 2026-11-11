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
 * <p>방 하나에 주인 1명 + 관전자 여럿. 페어 프로그래밍을 붙이면 "글을 쓸 수 있는 사람"에 수락된 상대 1명이 더해진다.
 */
@Component
class DeskRooms {

	record Room(String ownerId, String ownerNickname, long problemId, String language, Set<String> spectators) {

		Room withProblem(long problemId, String language) {
			return new Room(ownerId, ownerNickname, problemId, language, spectators);
		}

	}

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();

	/**
	 * 방을 연다. 이미 열려 있으면(문제·언어를 바꿈) 관전자는 그대로 두고 문제만 바꾼다.
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

	Optional<Room> leave(String roomId, String spectatorId) {
		Room room = rooms.get(roomId);
		if (room == null || !room.spectators().remove(spectatorId)) {
			return Optional.empty();
		}
		return Optional.of(room);
	}

	// 주인만 문서를 보낼 수 있다 (읽기 전용 강제). 관전자나 방 밖 사람이 보낸 것은 비어 있음
	Optional<Room> ownedBy(String roomId, String senderId) {
		Room room = rooms.get(roomId);
		return room != null && room.ownerId().equals(senderId) ? Optional.of(room) : Optional.empty();
	}

	Optional<Room> close(String ownerId) {
		return Optional.ofNullable(rooms.remove(ownerId));
	}

	// 연결이 끊긴 사람이 관전 중이던 방들에서 빠진다. 빠진 방들을 돌려줘 주인에게 관전자 수를 알린다
	List<Room> leaveAll(String spectatorId) {
		return rooms.values().stream()
				.filter(room -> room.spectators().remove(spectatorId))
				.toList();
	}

}
