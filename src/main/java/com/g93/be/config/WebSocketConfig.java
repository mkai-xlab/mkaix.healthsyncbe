package com.g93.be.config;

import com.g93.be.security.WebSocketChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketChannelInterceptor webSocketChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enables a simple memory-based message broker to carry the messages back to the client on destinations prefixed with "/topic" and "/queue"
        config.enableSimpleBroker("/topic", "/queue");
        // Prefix used to filter destinations handled by methods annotated with @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
        // Prefix used to send messages to specific users
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registers the "/ws" endpoint, enabling standard WebSockets
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // Configure CORS properly for production
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketChannelInterceptor);
    }
}
