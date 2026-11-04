package com.codingtest.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByGoogleSubject(String googleSubject);

	boolean existsByNickname(String nickname);

}
