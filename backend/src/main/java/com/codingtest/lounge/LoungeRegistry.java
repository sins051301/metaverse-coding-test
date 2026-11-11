package com.codingtest.lounge;

import com.codingtest.lounge.LoungeMessages.AvatarState;
import com.codingtest.lounge.LoungeMessages.JoinRequest;
import com.codingtest.lounge.LoungeMessages.MoveRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 지금 라운지에 있는 아바타와 책상 주인. 서버 메모리에만 둔다.
 *
 * <p>DB 에 두지 않는 이유: 위치는 초당 10번씩 바뀌고, 서버가 꺼지면 연결도 모두 끊겨 어차피 다시 입장한다. 잃어도 되는 값이다.
 * 한계: 서버가 여러 대면 각자 다른 목록을 갖는다. 그때는 Redis 같은 공용 저장소와 외부 브로커로 옮긴다 (README 10장).
 *
 * <p>ConcurrentHashMap: 여러 연결의 메시지가 서로 다른 스레드에서 동시에 들어온다. 일반 HashMap 은 동시에 고치면 깨질 수 있다.
 * 책상 점유는 putIfAbsent("비어 있을 때만 넣기")가 한 번에(원자적으로) 일어나서, 두 사람이 같은 순간 같은 책상에 앉아도 한 명만 성공한다.
 */
@Component
class LoungeRegistry {

	private static final Set<String> COATS = Set.of("ginger", "grey", "black", "white", "siamese");
	private static final Set<String> DIRECTIONS = Set.of("down", "up", "left", "right");
	private static final Pattern GUEST_NICKNAME = Pattern.compile("익명_[0-9A-F]{4}");

	private final Map<String, AvatarState> avatars = new ConcurrentHashMap<>();
	private final Map<Integer, String> deskOwners = new ConcurrentHashMap<>();

	AvatarState join(LoungeIdentity identity, JoinRequest request) {
		String avatarId = identity.avatarId();
		leave(avatarId);
		Integer requestedDesk = request.deskId();
		boolean seated = requestedDesk != null && LoungeMap.isDesk(requestedDesk)
				&& deskOwners.putIfAbsent(requestedDesk, avatarId) == null;
		AvatarState avatar = new AvatarState(
				avatarId,
				identity.member() ? identity.nickname() : guestNickname(request.nickname()),
				identity.member(),
				COATS.contains(request.coat()) ? request.coat() : "ginger",
				LoungeMap.clampX(request.x()),
				LoungeMap.clampY(request.y()),
				direction(request.direction()),
				false,
				seated ? requestedDesk : null);
		avatars.put(avatarId, avatar);
		return avatar;
	}

	// 앉아 있는 고양이는 움직이지 않는다. 늦게 도착한 이동 메시지가 앉은 고양이를 옮기지 않게 무시한다
	Optional<AvatarState> move(String avatarId, MoveRequest request) {
		return Optional.ofNullable(avatars.computeIfPresent(avatarId, (id, avatar) -> avatar.deskId() != null
				? avatar
				: avatar.movedTo(LoungeMap.clampX(request.x()), LoungeMap.clampY(request.y()),
						direction(request.direction()), request.moving())));
	}

	/**
	 * @return 앉았으면 바뀐 상태, 없는 책상이거나 이미 남이 앉아 있으면 비어 있음
	 */
	Optional<AvatarState> sit(String avatarId, int deskId) {
		AvatarState avatar = avatars.get(avatarId);
		if (avatar == null || !LoungeMap.isDesk(deskId)) {
			return Optional.empty();
		}
		String owner = deskOwners.putIfAbsent(deskId, avatarId);
		if (owner != null && !owner.equals(avatarId)) {
			return Optional.empty();
		}
		if (avatar.deskId() != null && avatar.deskId() != deskId) {
			deskOwners.remove(avatar.deskId(), avatarId);
		}
		AvatarState seated = avatar.seatedAt(deskId);
		avatars.put(avatarId, seated);
		return Optional.of(seated);
	}

	Optional<AvatarState> stand(String avatarId) {
		AvatarState avatar = avatars.get(avatarId);
		if (avatar == null || avatar.deskId() == null) {
			return Optional.empty();
		}
		deskOwners.remove(avatar.deskId(), avatarId);
		AvatarState standing = avatar.seatedAt(null);
		avatars.put(avatarId, standing);
		return Optional.of(standing);
	}

	Optional<AvatarState> leave(String avatarId) {
		AvatarState removed = avatars.remove(avatarId);
		if (removed != null && removed.deskId() != null) {
			deskOwners.remove(removed.deskId(), avatarId);
		}
		return Optional.ofNullable(removed);
	}

	List<AvatarState> all() {
		return List.copyOf(avatars.values());
	}

	private static String direction(String direction) {
		return DIRECTIONS.contains(direction) ? direction : "down";
	}

	// 게스트가 보낸 닉네임은 익명_XXXX 모양일 때만 쓴다. 아니면 서버가 새로 뽑는다 (회원 닉네임 흉내 방지)
	private static String guestNickname(String requested) {
		if (requested != null && GUEST_NICKNAME.matcher(requested).matches()) {
			return requested;
		}
		return "익명_%04X".formatted(ThreadLocalRandom.current().nextInt(0x10000));
	}

}
