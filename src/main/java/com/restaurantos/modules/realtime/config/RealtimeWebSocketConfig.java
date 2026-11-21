package com.restaurantos.modules.realtime.config;

import com.restaurantos.modules.realtime.security.RealtimeChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final RealtimeChannelInterceptor security;
    private final String[] origins;

    public RealtimeWebSocketConfig(RealtimeChannelInterceptor security,
            @Value("${restaurantos.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.security = security;
        this.origins = java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(s -> !s.isBlank()).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/realtime").setAllowedOrigins(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic/");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(security);
    }
}
