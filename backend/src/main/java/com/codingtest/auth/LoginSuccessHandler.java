package com.codingtest.auth;

import com.codingtest.user.User;
import com.codingtest.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Google 로그인이 끝나면 회원을 찾거나 만들고, 우리 서버의 JWT 를 HttpOnly 쿠키로 심은 뒤 프론트엔드의 {@code /login/callback} 으로 돌려보낸다.
 * 토큰이 URL 에도, 프론트엔드 자바스크립트에도 드러나지 않는다 (쿠키 속성은 AccessTokenCookie).
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserService userService;
	private final TokenService tokenService;
	private final AccessTokenCookie accessTokenCookie;
	private final String frontendUrl;

	public LoginSuccessHandler(UserService userService, TokenService tokenService, AccessTokenCookie accessTokenCookie,
			@Value("${app.frontend-url}") String frontendUrl) {
		this.userService = userService;
		this.tokenService = tokenService;
		this.accessTokenCookie = accessTokenCookie;
		this.frontendUrl = frontendUrl;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
		OidcUser googleUser = (OidcUser) authentication.getPrincipal();
		User user = userService.findOrCreate(googleUser.getSubject(), googleUser.getEmail());

		// 세션은 Google 로그인 과정(state 검증)에만 쓰고 버린다. 남겨 두면 이후 요청이 JWT 가 아닌 세션 쿠키로 인증되어 버린다.
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}

		response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.create(tokenService.issue(user.getId())).toString());
		response.sendRedirect(frontendUrl + "/login/callback");
	}

}
