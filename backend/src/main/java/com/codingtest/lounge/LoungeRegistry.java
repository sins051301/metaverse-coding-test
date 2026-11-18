package com.codingtest.lounge;

import com.codingtest.lounge.LoungeMessages.AvatarState;
import com.codingtest.lounge.LoungeMessages.ChatMessage;
import com.codingtest.lounge.LoungeMessages.JoinRequest;
import com.codingtest.lounge.LoungeMessages.MoveRequest;
import com.codingtest.lounge.LoungeMessages.RecentSolve;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
	private static final Pattern BROWSER_KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	private static final Set<String> ACTIVITIES = Set.of("idle", "typing", "napping");
	private static final int RECENT_SOLVE_LIMIT = 5;
	static final int CHAT_MAX_LENGTH = 200;
	private static final int RECENT_CHAT_LIMIT = 30;
	// 도배 방지: 한 아바타는 이 간격 안에 한 번만 말할 수 있다 (게스트도 쓰므로 서버가 막는다)
	private static final Duration CHAT_INTERVAL = Duration.ofSeconds(1);

	private final Map<String, AvatarState> avatars = new ConcurrentHashMap<>();
	private final Map<Integer, String> deskOwners = new ConcurrentHashMap<>();
	// 회원 아바타의 users.id. 방송에는 싣지 않고, "이 회원이 풀었다" 를 그 회원의 아바타들에 이어 줄 때만 쓴다
	private final Map<String, Long> avatarUsers = new ConcurrentHashMap<>();
	// 최근 푼 기록. 새것이 앞. 여러 채점 스레드가 동시에 넣으므로 synchronized 로 감싸서 쓴다
	private final Deque<RecentSolve> recentSolves = new ArrayDeque<>();
	// 최근 채팅. 오래된 것이 앞. recentSolves 처럼 synchronized 로 감싸서 쓴다
	private final Deque<ChatMessage> recentChats = new ArrayDeque<>();
	private final Map<String, Instant> lastChatAt = new ConcurrentHashMap<>();
	// 한 사람에 고양이 한 마리: 사람 키(회원은 user:{id}, 게스트는 browser:{browserKey}) → 지금 그 사람의 아바타
	private final Map<String, String> avatarOfOwner = new ConcurrentHashMap<>();
	private final Map<String, String> ownerOfAvatar = new ConcurrentHashMap<>();

	/**
	 * @param avatar   새로 들어온 아바타
	 * @param replaced 같은 사람이 다른 탭에 두고 있던 아바타. 지웠으니 퇴장을 알리고 그 탭에 replaced 를 보낸다
	 */
	record Joined(AvatarState avatar, Optional<AvatarState> replaced) {
	}

	/**
	 * 같은 사람이 이미 다른 탭(연결)에 고양이를 두고 있으면 그 고양이를 지우고 새 고양이를 넣는다.
	 * 회원은 쿠키의 userId 로 판별해 기기가 달라도 한 마리다. 게스트는 믿을 신원이 없어 브라우저가 보낸 browserKey 로 판별한다.
	 * browserKey 는 위조할 수 있지만 남의 키는 방송되지 않아 알 수 없고, 맞혀도 게스트 고양이 하나가 빠질 뿐이다.
	 */
	Joined join(LoungeIdentity identity, JoinRequest request) {
		String avatarId = identity.avatarId();
		leave(avatarId);
		Optional<AvatarState> replaced = ownerKey(identity, request.browserKey())
				.flatMap(ownerKey -> {
					ownerOfAvatar.put(avatarId, ownerKey);
					return Optional.ofNullable(avatarOfOwner.put(ownerKey, avatarId));
				})
				.flatMap(this::leave);
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
				seated ? requestedDesk : null,
				"idle");
		avatars.put(avatarId, avatar);
		if (identity.member()) {
			avatarUsers.put(avatarId, identity.userId());
		}
		return new Joined(avatar, replaced);
	}

	private static Optional<String> ownerKey(LoungeIdentity identity, String browserKey) {
		if (identity.member()) {
			return Optional.of("user:" + identity.userId());
		}
		if (browserKey != null && BROWSER_KEY.matcher(browserKey).matches()) {
			return Optional.of("browser:" + browserKey);
		}
		return Optional.empty();
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

	// 서 있는 고양이는 타이핑·낮잠이 없다. 일어난 뒤 늦게 온 활동 알림은 무시한다
	Optional<AvatarState> setActivity(String avatarId, String activity) {
		String checked = ACTIVITIES.contains(activity) ? activity : "idle";
		return Optional.ofNullable(avatars.computeIfPresent(avatarId, (id, avatar) ->
				avatar.deskId() == null ? avatar : avatar.withActivity(checked)));
	}

	List<String> avatarIdsOf(long userId) {
		return avatarUsers.entrySet().stream()
				.filter(entry -> entry.getValue() == userId)
				.map(Map.Entry::getKey)
				.toList();
	}

	void recordSolve(RecentSolve solve) {
		synchronized (recentSolves) {
			recentSolves.addFirst(solve);
			while (recentSolves.size() > RECENT_SOLVE_LIMIT) {
				recentSolves.removeLast();
			}
		}
	}

	List<RecentSolve> recentSolves() {
		synchronized (recentSolves) {
			return List.copyOf(recentSolves);
		}
	}

	sealed interface ChatResult permits Accepted, Rejected {
	}

	record Accepted(ChatMessage chat) implements ChatResult {
	}

	record Rejected(String reason) implements ChatResult {
	}

	/**
	 * 채팅 한 줄을 받는다. 입장한 아바타만, 공백을 뺀 1~200자만, 직전 메시지에서 1초가 지났을 때만.
	 */
	ChatResult chat(String avatarId, String rawText, Instant now) {
		AvatarState avatar = avatars.get(avatarId);
		String text = rawText == null ? "" : rawText.strip();
		if (avatar == null || text.isEmpty()) {
			return new Rejected("empty");
		}
		if (text.length() > CHAT_MAX_LENGTH) {
			return new Rejected("too-long");
		}
		Instant previous = lastChatAt.put(avatarId, now);
		if (previous != null && Duration.between(previous, now).compareTo(CHAT_INTERVAL) < 0) {
			lastChatAt.put(avatarId, previous);
			return new Rejected("too-fast");
		}
		ChatMessage chat = new ChatMessage(avatarId, avatar.nickname(), text, now);
		synchronized (recentChats) {
			recentChats.addLast(chat);
			while (recentChats.size() > RECENT_CHAT_LIMIT) {
				recentChats.removeFirst();
			}
		}
		return new Accepted(chat);
	}

	List<ChatMessage> recentChats() {
		synchronized (recentChats) {
			return List.copyOf(recentChats);
		}
	}

	Optional<AvatarState> leave(String avatarId) {
		String ownerKey = ownerOfAvatar.remove(avatarId);
		if (ownerKey != null) {
			avatarOfOwner.remove(ownerKey, avatarId);
		}
		avatarUsers.remove(avatarId);
		lastChatAt.remove(avatarId);
		AvatarState removed = avatars.remove(avatarId);
		if (removed != null && removed.deskId() != null) {
			deskOwners.remove(removed.deskId(), avatarId);
		}
		return Optional.ofNullable(removed);
	}

	Optional<AvatarState> find(String avatarId) {
		return Optional.ofNullable(avatars.get(avatarId));
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
