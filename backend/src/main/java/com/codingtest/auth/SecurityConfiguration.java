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
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfiguration {

	private static final int MINIMUM_SECRET_BYTES = 32;
	private static final String LOUNGE_WEB_SOCKET_PATH = "/ws";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler, BearerTokenResolver bearerTokenResolver) {
		http
				// 토큰 쿠키는 SameSite=Lax 라 다른 사이트에서 보낸 POST 에는 붙지 않는다. 그래서 CSRF 토큰 없이도 남의 사이트가 내 이름으로 실행·제출하지 못한다.
				// 이 전제가 깨지지 않도록 상태를 바꾸는 API 를 GET 으로 만들지 않는다 (Lax 는 다른 사이트에서 링크로 여는 GET 에는 쿠키를 붙인다).
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/api/problems", "/api/problems/*").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
						// 라운지 WebSocket. 게스트도 들어와야 하고, 신원은 LoungeHandshakeHandler 가 쿠키로 직접 정한다
						.requestMatchers(LOUNGE_WEB_SOCKET_PATH).permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
						.anyRequest().authenticated())
				.oauth2Login(login -> login.successHandler(loginSuccessHandler))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.bearerTokenResolver(bearerTokenResolver)
						.jwt(Customizer.withDefaults()))
				// API 는 토큰이 없을 때 Google 로그인 화면으로 보내지 않고 401 로 답한다.
				.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
						new BearerTokenAuthenticationEntryPoint(), PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
		return http.build();
	}

	/**
	 * 토큰을 Authorization 헤더에서 먼저 찾고, 없으면 accessToken 쿠키에서 찾는다.
	 * 브라우저(프론트엔드)는 HttpOnly 쿠키로, Swagger 와 테스트는 헤더(Authorize 버튼)로 보낸다.
	 *
	 * <p>라운지 WebSocket(/ws)에서는 찾지 않는다. 여기서 토큰을 돌려주면 만료된 쿠키가 남은 사람은 permitAll 이어도 검증 실패로 401 이 되어
	 * 게스트로도 들어오지 못한다. /ws 의 신원은 LoungeHandshakeHandler 가 쿠키를 직접 확인해 정하고, 틀린 쿠키는 게스트로 받는다.
	 */
	@Bean
	BearerTokenResolver bearerTokenResolver(AccessTokenCookie accessTokenCookie) {
		DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
		return request -> {
			if (LOUNGE_WEB_SOCKET_PATH.equals(request.getRequestURI())) {
				return null;
			}
			String headerToken = headerResolver.resolve(request);
			return headerToken != null ? headerToken : accessTokenCookie.read(request).orElse(null);
		};
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
