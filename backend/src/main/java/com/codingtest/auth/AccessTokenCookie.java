package com.codingtest.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 액세스 토큰(JWT)을 담는 쿠키. 만들기·지우기·요청에서 읽기를 한곳에 모았다.
 *
 * <ul>
 *   <li>HttpOnly: 자바스크립트가 읽을 수 없다. 페이지에 남이 심은 스크립트(XSS)가 돌아도 토큰을 훔쳐 가지 못한다</li>
 *   <li>SameSite=Lax: 다른 사이트에서 보낸 POST 에는 쿠키가 붙지 않는다. 그래서 CSRF 토큰 없이도 남의 사이트가 내 이름으로 제출하지 못한다</li>
 *   <li>Secure: HTTPS 에서만 보낸다. 로컬(http://localhost)에서는 꺼야 쿠키가 붙으므로 설정값으로 둔다</li>
 *   <li>Domain 을 지정하지 않아 localhost 에만 붙는다. 쿠키는 포트를 가리지 않아 8080 이 심은 쿠키가 3000(프론트 dev 서버 프록시)으로 보낸 요청에도 붙는다</li>
 * </ul>
 */
@Component
public class AccessTokenCookie {

	static final String NAME = "accessToken";

	private final Duration expiration;
	private final boolean secure;

	public AccessTokenCookie(@Value("${jwt.expiration}") Duration expiration, @Value("${app.cookie-secure}") boolean secure) {
		this.expiration = expiration;
		this.secure = secure;
	}

	public ResponseCookie create(String token) {
		return base(token).maxAge(expiration).build();
	}

	public ResponseCookie expire() {
		return base("").maxAge(Duration.ZERO).build();
	}

	public Optional<String> read(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return Optional.empty();
		}
		return Arrays.stream(request.getCookies())
				.filter(cookie -> NAME.equals(cookie.getName()))
				.map(Cookie::getValue)
				.filter(value -> !value.isBlank())
				.findFirst();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path("/");
	}

}
