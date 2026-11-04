package com.codingtest.user;

public record UserResponse(long id, String nickname, String email) {

	static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getNickname(), user.getEmail());
	}

}
