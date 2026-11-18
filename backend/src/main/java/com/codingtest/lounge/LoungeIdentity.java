package com.codingtest.lounge;

import java.security.Principal;

/**
 * WebSocket 연결 하나의 신원. 연결할 때(핸드셰이크) LoungeHandshakeHandler 가 정하고, 연결이 끝날 때까지 바뀌지 않는다.
 * 메시지를 처리할 때는 메시지 본문의 값이 아니라 이 신원만 믿는다. 본문의 id 를 믿으면 누구나 남의 고양이를 움직일 수 있다.
 *
 * @param avatarId 연결마다 새로 만드는 아바타 id. 같은 회원이 다른 탭으로 들어오면 새 id 를 받고, 이전 탭의 아바타는 빠진다 (LoungeRegistry.join).
 *                 getName() 으로 쓰여서 "나에게만 보내기"(/user/queue/...)의 받는 사람 주소가 된다
 * @param userId   회원이면 users.id, 게스트면 null
 * @param nickname 회원이면 DB 닉네임. 게스트는 null 이고, 입장 메시지의 익명_XXXX 를 검사해 쓴다
 */
public record LoungeIdentity(String avatarId, Long userId, String nickname) implements Principal {

	@Override
	public String getName() {
		return avatarId;
	}

	boolean member() {
		return userId != null;
	}

}
