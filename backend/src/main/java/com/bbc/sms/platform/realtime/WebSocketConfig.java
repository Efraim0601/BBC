package com.bbc.sms.platform.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * STOMP over WebSocket. Clients connect to /ws and subscribe to
 * /topic/school/{schoolId}/... — CONNECT frames are authenticated by the JWT
 * passed in the STOMP "Authorization" header.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final RealtimeChannelInterceptor authorization;
    private final String[] allowedOrigins;

    public WebSocketConfig(RealtimeChannelInterceptor authorization,
                           @Value("${bbc.cors.allowed-origins}") String allowedOrigins) {
        this.authorization = authorization;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorization);
    }
}
