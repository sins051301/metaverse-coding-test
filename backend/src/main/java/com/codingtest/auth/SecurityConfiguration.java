package com.codingtest.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfiguration {

	private static final int MINIMUM_SECRET_BYTES = 32;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler) {
		http
				// 인증은 브라우저가 자동으로 보내지 않는 Authorization 헤더로만 하므로, 쿠키를 노리는 CSRF 공격이 성립하지 않는다.
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/api/problems", "/api/problems/*").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
						.anyRequest().authenticated())
				.oauth2Login(login -> login.successHandler(loginSuccessHandler))
				.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
				// API 는 토큰이 없을 때 Google 로그인 화면으로 보내지 않고 401 로 답한다.
				.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
						new BearerTokenAuthenticationEntryPoint(), PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
		return http.build();
	}

	@Bean
	SecretKey jwtSecretKey(@Value("${jwt.secret}") String secret) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < MINIMUM_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET 은 HS256 에 필요한 " + MINIMUM_SECRET_BYTES + "바이트 이상이어야 합니다.");
		}
		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSecretKey).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

}
