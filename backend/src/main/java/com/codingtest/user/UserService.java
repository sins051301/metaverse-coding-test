package com.codingtest.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class UserService {

	private static final String NICKNAME_PREFIX = "사용자_";
	private static final String NICKNAME_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int NICKNAME_SUFFIX_LENGTH = 6;

	private final UserRepository userRepository;
	private final SecureRandom random = new SecureRandom();

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Google 계정 ID(sub)로 회원을 찾고, 처음 로그인이면 겹치지 않는 닉네임으로 새로 만든다.
	 */
	@Transactional
	public User findOrCreate(String googleSubject, String email) {
		return userRepository.findByGoogleSubject(googleSubject)
				.orElseGet(() -> userRepository.save(new User(googleSubject, email, uniqueNickname(), Instant.now())));
	}

	@Transactional(readOnly = true)
	public User get(long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원이 없습니다: " + id));
	}

	private String uniqueNickname() {
		String nickname;
		do {
			StringBuilder suffix = new StringBuilder();
			for (int index = 0; index < NICKNAME_SUFFIX_LENGTH; index++) {
				suffix.append(NICKNAME_CHARACTERS.charAt(random.nextInt(NICKNAME_CHARACTERS.length())));
			}
			nickname = NICKNAME_PREFIX + suffix;
		} while (userRepository.existsByNickname(nickname));
		return nickname;
	}

}
