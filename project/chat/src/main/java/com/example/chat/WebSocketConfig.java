// WebSocketConfig.java
package com.example.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@EnableWebSocketMessageBroker
public class    WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final AppUserRepository userRepo;

    public WebSocketConfig(JwtDecoder jwtDecoder, AppUserRepository userRepo) {
        this.jwtDecoder = jwtDecoder;
        this.userRepo = userRepo;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:5173")
                .setHandshakeHandler(new JwtHandshakeHandler(jwtDecoder, userRepo))
                .withSockJS();
        // Test / Postman (native WebSocket)
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new JwtHandshakeHandler(jwtDecoder, userRepo));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app"); // gönderim
        registry.enableSimpleBroker("/topic", "/queue");    // abonelik
        registry.setUserDestinationPrefix("/user");         // /user/queue/...
    }
}
