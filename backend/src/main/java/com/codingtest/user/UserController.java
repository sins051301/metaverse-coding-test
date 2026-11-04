package com.codingtest.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@SecurityRequirement(name = "bearer")
@RestController
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "내 정보", description = "토큰이 제대로 들어갔는지 확인할 때 쓴다")
	@GetMapping("/api/users/me")
	public UserResponse me(@AuthenticationPrincipal Jwt token) {
		return UserResponse.from(userService.get(Long.parseLong(token.getSubject())));
	}

}
