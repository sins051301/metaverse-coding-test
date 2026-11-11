package com.codingtest.lounge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 우체국 설정.
 *
 * <ul>
 *   <li>/ws: 브라우저가 WebSocket 을 여는 주소. SockJS(WebSocket 이 막힌 환경용 대체 통신)는 쓰지 않는다. 지원 브라우저가 모두 WebSocket 을 쓴다</li>
 *   <li>/app/...: 클라이언트가 서버에 보내는(SEND) 주소. LoungeController 의 @MessageMapping 이 받는다</li>
 *   <li>/topic/...: 구독한 모두에게 뿌리는(방송) 주소. /user/queue/...: 특정 연결 하나에게만 보내는 주소</li>
 *   <li>simple broker: 구독자 목록을 서버 메모리에 두는 내장 우체국. 서버 1대면 충분하고, 여러 대가 되면 RabbitMQ 같은 외부 브로커로 바꾼다</li>
 * </ul>
 *
 * 허용 출처(Origin)는 프론트 주소 하나뿐이다. 다른 사이트가 내 브라우저로 이 주소에 연결해 쿠키를 악용하는 공격(Cross-Site WebSocket Hijacking)을 막는다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class LoungeWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

	private final LoungeHandshakeHandler loungeHandshakeHandler;
	private final String frontendUrl;

	public LoungeWebSocketConfiguration(LoungeHandshakeHandler loungeHandshakeHandler, @Value("${app.frontend-url}") String frontendUrl) {
		this.loungeHandshakeHandler = loungeHandshakeHandler;
		this.frontendUrl = frontendUrl;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOrigins(frontendUrl)
				.setHandshakeHandler(loungeHandshakeHandler);
		// 받은 메시지는 스레드 여러 개가 나눠 처리한다. 순서를 지키라고 하지 않으면 한 사람이 보낸 이동 1, 2, 3 이 2, 1, 3 으로 처리될 수 있다
		registry.setPreserveReceiveOrder(true);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
		// 보낼 때도 같은 연결로 가는 메시지는 처리한 순서대로 보낸다 (받는 쪽 화면에서 고양이가 뒤로 튀지 않게)
		registry.setPreservePublishOrder(true);
	}

}
