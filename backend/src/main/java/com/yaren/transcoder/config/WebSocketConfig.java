package com.yaren.transcoder.config;

import com.yaren.transcoder.security.JwtUtil;
import com.yaren.transcoder.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public WebSocketConfig(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-party-v2")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    String jwt = null;

                    if (authorization != null && !authorization.isEmpty()) {
                        String authHeader = authorization.get(0);
                        if (authHeader.startsWith("Bearer ")) {
                            jwt = authHeader.substring(7);
                        }
                    }

                    if (jwt != null) {
                        try {
                            String username = jwtUtil.extractUsername(jwt);
                            if (username != null) {
                                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                if (jwtUtil.validateToken(jwt, userDetails)) {
                                    UsernamePasswordAuthenticationToken authenticationToken =
                                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                                    accessor.setUser(authenticationToken);
                                }
                            }
                        } catch (Exception e) {
                            // Token is invalid or expired
                            throw new IllegalArgumentException("Invalid Token");
                        }
                    } else {
                        throw new IllegalArgumentException("Missing Token");
                    }
                }
                return message;
            }
        });
    }
}
