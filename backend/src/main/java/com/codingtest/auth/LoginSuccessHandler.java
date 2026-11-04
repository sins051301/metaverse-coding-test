package com.codingtest.auth;

import com.codingtest.user.User;
import com.codingtest.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

/**
 * Google 로그인이 끝나면 회원을 찾거나 만들고, 우리 서버의 JWT 를 {"accessToken": "..."} 로 돌려준다.
 * 프론트엔드가 생기면 로그인 팝업에 토큰을 넘기는 방식으로 바꾼다.
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserService userService;
	private final TokenService tokenService;
	private final JsonMapper jsonMapper;

	public LoginSuccessHandler(UserService userService, TokenService tokenService, JsonMapper jsonMapper) {
		this.userService = userService;
		this.tokenService = tokenService;
		this.jsonMapper = jsonMapper;
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

		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getWriter(), Map.of("accessToken", tokenService.issue(user.getId())));
	}

}
