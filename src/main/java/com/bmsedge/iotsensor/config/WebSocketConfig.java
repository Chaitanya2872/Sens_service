package com.bmsedge.iotsensor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ UPDATED: Register endpoint with both SockJS and native WebSocket
        registry.addEndpoint("/ws-cafeteria")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // ✅ Native WebSocket endpoint (what we're using now)
        registry.addEndpoint("/ws-cafeteria")
                .setAllowedOriginPatterns("*");
    }
}