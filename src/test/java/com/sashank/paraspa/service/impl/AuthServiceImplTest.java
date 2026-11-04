package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.RegisterRequest;
import com.sashank.paraspa.dto.response.AuthResponse;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.dto.request.LoginRequest;
import com.sashank.paraspa.exception.AlreadyExistsException;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private com.sashank.paraspa.service.RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_shouldThrowAlreadyExistsException_whenEmailAlreadyRegistered() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Email already registered");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldEncodePassword_beforeSaving() {
        String rawPassword = "password123";
        String encodedPassword = "encoded-password-hash";

        RegisterRequest request = RegisterRequest.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .password(rawPassword)
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtTokenProvider.generateToken(request.getEmail(), 1L)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(passwordEncoder).encode(rawPassword);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getPassword()).isEqualTo(encodedPassword);
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo(rawPassword);
        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_shouldThrowBadRequestException_whenPasswordDoesNotMatch() {
        String rawPassword = "wrong-password";
        String storedEncodedPassword = "encoded-password-hash";

        LoginRequest request = LoginRequest.builder()
                .email("jane@example.com")
                .password(rawPassword)
                .build();

        User user = User.builder()
                .id(1L)
                .email(request.getEmail())
                .password(storedEncodedPassword)
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, storedEncodedPassword)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid email or password");

        verify(jwtTokenProvider, never()).generateToken(anyString(), any());
    }

    @Test
    void login_shouldReturnJwtToken_whenCredentialsAreValid() {
        String rawPassword = "password123";
        String storedEncodedPassword = "encoded-password-hash";

        LoginRequest request = LoginRequest.builder()
                .email("jane@example.com")
                .password(rawPassword)
                .build();

        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email(request.getEmail())
                .password(storedEncodedPassword)
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, storedEncodedPassword)).thenReturn(true);
        when(jwtTokenProvider.generateToken(user.getEmail(), user.getId())).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getEmail()).isEqualTo(user.getEmail());
    }

    // ------------------------------------------------------------------ refresh tokens

    private com.sashank.paraspa.entity.User user() {
        return com.sashank.paraspa.entity.User.builder().id(7L).name("Jane").email("jane@example.com").password("hash").build();
    }

    @Test
    void login_returnsAnAccessAndARefreshToken_andHowLongTheAccessTokenLives() {
        com.sashank.paraspa.entity.User user = user();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(jwtTokenProvider.generateToken("jane@example.com", 7L)).thenReturn("access");
        when(jwtTokenProvider.getAccessTokenSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(user)).thenReturn("refresh");

        var response = authService.login(com.sashank.paraspa.dto.request.LoginRequest.builder().email("jane@example.com").password("pw").build());

        assertThat(response.getToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
    }

    @Test
    void register_alsoReturnsARefreshToken() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(com.sashank.paraspa.entity.User.class))).thenAnswer(i -> {
            com.sashank.paraspa.entity.User u = i.getArgument(0);
            u.setId(7L);
            return u;
        });
        when(refreshTokenService.issue(any())).thenReturn("refresh");

        var response = authService.register(RegisterRequest.builder().name("Jane").email("jane@example.com").password("password123").build());

        assertThat(response.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    void refresh_consumesTheOldTokenAndIssuesAWholeNewPair() {
        com.sashank.paraspa.entity.User user = user();
        when(refreshTokenService.consume("old")).thenReturn(user);
        when(jwtTokenProvider.generateToken("jane@example.com", 7L)).thenReturn("new-access");
        when(refreshTokenService.issue(user)).thenReturn("new-refresh");

        var response = authService.refresh("old");

        assertThat(response.getToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(response.getId()).isEqualTo(7L);
    }

    @Test
    void refresh_withABadToken_issuesNothing() {
        when(refreshTokenService.consume("bad")).thenThrow(new com.sashank.paraspa.exception.UnauthorizedException("Invalid or expired refresh token"));

        assertThatThrownBy(() -> authService.refresh("bad")).isInstanceOf(com.sashank.paraspa.exception.UnauthorizedException.class);
        verify(refreshTokenService, never()).issue(any());
        verify(jwtTokenProvider, never()).generateToken(any(), any());
    }

    @Test
    void logout_revokesTheRefreshToken() {
        authService.logout("tok");

        verify(refreshTokenService).revoke("tok");
    }
}
