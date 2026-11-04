package com.codingtest.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 우리 서버의 액세스 토큰(JWT, HS256)을 발급한다. subject 는 users.id 다.
 */
@Service
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final Duration expiration;

	public TokenService(JwtEncoder jwtEncoder, @Value("${jwt.expiration}") Duration expiration) {
		this.jwtEncoder = jwtEncoder;
		this.expiration = expiration;
	}

	public String issue(long userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(now.plus(expiration))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
