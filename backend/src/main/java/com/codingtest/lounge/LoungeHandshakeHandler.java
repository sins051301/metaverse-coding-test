package com.codingtest.lounge;

import com.codingtest.auth.AccessTokenCookie;
import com.codingtest.user.UserService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WebSocket 연결을 받을 때(핸드셰이크) 그 연결의 신원(LoungeIdentity)을 정한다.
 *
 * <p>핸드셰이크 = WebSocket 이 열리기 전에 한 번 오가는 평범한 HTTP 요청(Upgrade: websocket). 브라우저는 여기에 쿠키를 자동으로 붙인다.
 * 그래서 로그인 때 심은 accessToken HttpOnly 쿠키를 이 요청에서 읽어 JWT 를 검증한다. 자바스크립트는 쿠키를 읽지 못하므로 토큰을 따로 보낼 수도 없다.
 *
 * <p>쿠키가 없거나, 만료됐거나, 위조됐으면 게스트로 받는다. 라운지는 로그인 없이 들어올 수 있어야 하기 때문이다 (FR-01-1).
 */
@Component
public class LoungeHandshakeHandler extends DefaultHandshakeHandler {

	private final AccessTokenCookie accessTokenCookie;
	private final JwtDecoder jwtDecoder;
	private final UserService userService;

	public LoungeHandshakeHandler(AccessTokenCookie accessTokenCookie, JwtDecoder jwtDecoder, UserService userService) {
		this.accessTokenCookie = accessTokenCookie;
		this.jwtDecoder = jwtDecoder;
		this.userService = userService;
	}

	@Override
	protected Principal determineUser(ServerHttpRequest request, WebSocketHandler webSocketHandler, Map<String, Object> attributes) {
		String avatarId = "a-" + UUID.randomUUID().toString().substring(0, 8);
		if (request instanceof ServletServerHttpRequest servletRequest) {
			Optional<Long> userId = accessTokenCookie.read(servletRequest.getServletRequest()).flatMap(this::decodeUserId);
			Optional<String> nickname = userId.flatMap(userService::findNickname);
			if (userId.isPresent() && nickname.isPresent()) {
				return new LoungeIdentity(avatarId, userId.get(), nickname.get());
			}
		}
		return new LoungeIdentity(avatarId, null, null);
	}

	private Optional<Long> decodeUserId(String token) {
		try {
			return Optional.of(Long.parseLong(jwtDecoder.decode(token).getSubject()));
		} catch (JwtException exception) {
			return Optional.empty();
		}
	}

}
