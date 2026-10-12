package com.sashank.paraspa.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private JwtChannelInterceptor jwtChannelInterceptor;

    private Message<byte[]> buildConnectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_shouldSetPrincipal_whenTokenIsValid() {
        Message<byte[]> message = buildConnectMessage("Bearer valid-token");

        when(jwtTokenProvider.extractTokenFromBearer("Bearer valid-token")).thenReturn("valid-token");
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("valid-token")).thenReturn("jane@example.com");

        jwtChannelInterceptor.preSend(message, null);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("jane@example.com");
    }

    @Test
    void preSend_shouldThrow_whenTokenIsInvalid() {
        Message<byte[]> message = buildConnectMessage("Bearer bad-token");

        when(jwtTokenProvider.extractTokenFromBearer("Bearer bad-token")).thenReturn("bad-token");
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preSend_shouldThrow_whenAuthorizationHeaderMissing() {
        Message<byte[]> message = buildConnectMessage(null);

        when(jwtTokenProvider.extractTokenFromBearer(null)).thenReturn(null);

        assertThatThrownBy(() -> jwtChannelInterceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preSend_shouldPassThrough_forNonConnectCommands() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = jwtChannelInterceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }
}
