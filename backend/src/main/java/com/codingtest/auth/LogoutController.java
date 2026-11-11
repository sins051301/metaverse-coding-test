package com.codingtest.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
public class LogoutController {

	private final AccessTokenCookie accessTokenCookie;

	public LogoutController(AccessTokenCookie accessTokenCookie) {
		this.accessTokenCookie = accessTokenCookie;
	}

	@Operation(summary = "로그아웃", description = "토큰 쿠키를 지운다. HttpOnly 라 프론트엔드가 직접 지울 수 없어서 서버가 만료된 쿠키로 덮어쓴다")
	@PostMapping("/api/auth/logout")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, accessTokenCookie.expire().toString()).build();
	}

}
