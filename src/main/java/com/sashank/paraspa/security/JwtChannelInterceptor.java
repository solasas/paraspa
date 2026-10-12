package com.sashank.paraspa.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            String bearerToken = (authHeaders != null && !authHeaders.isEmpty()) ? authHeaders.get(0) : null;
            String token = jwtTokenProvider.extractTokenFromBearer(bearerToken);

            if (token == null || !jwtTokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("Invalid or missing WebSocket authentication token");
            }

            String email = jwtTokenProvider.getEmailFromToken(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
            accessor.setUser(authentication);
        }

        return message;
    }
}
