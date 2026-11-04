package com.codingtest.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String googleSubject;

	private String email;

	private String nickname;

	// LocalDateTime 은 시간대가 없어 Hibernate 가 JVM 시간대(KST)의 시각으로 보고 UTC 로 한 번 더 바꾼다(9시간 어긋남).
	// 시점이 하나로 정해지는 Instant 로 두면 hibernate.jdbc.time_zone=UTC 에 따라 UTC 로 그대로 저장된다.
	private Instant createdAt;

	User(String googleSubject, String email, String nickname, Instant createdAt) {
		this.googleSubject = googleSubject;
		this.email = email;
		this.nickname = nickname;
		this.createdAt = createdAt;
	}

}
