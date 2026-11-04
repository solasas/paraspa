package com.sashank.paraspa.security;

import com.sashank.paraspa.enums.UserRole;
import com.sashank.paraspa.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private void validToken(String token, long userId, String email) {
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn(email);
    }

    @Test
    void anAdmin_getsROLE_ADMIN_fromTheirCurrentRoleInTheDatabase() throws Exception {
        validToken("t", 7L, "admin@example.com");
        when(userRepository.findRoleById(7L)).thenReturn(Optional.of(UserRole.ADMIN));
        MockHttpServletRequest request = requestWithToken("t");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("admin@example.com");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(request.getAttribute("userId")).isEqualTo(7L);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anOrdinaryUser_getsROLE_USER_only() throws Exception {
        validToken("t", 8L, "user@example.com");
        when(userRepository.findRoleById(8L)).thenReturn(Optional.of(UserRole.USER));

        filter.doFilter(requestWithToken("t"), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void theRoleIsReadOnEveryRequest_soADemotionTakesEffectAtOnce() throws Exception {
        validToken("t", 7L, "was-admin@example.com");
        when(userRepository.findRoleById(7L)).thenReturn(Optional.of(UserRole.ADMIN), Optional.of(UserRole.USER));

        filter.doFilter(requestWithToken("t"), new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");

        SecurityContextHolder.clearContext();   // next request: same token, role changed in the database meanwhile
        filter.doFilter(requestWithToken("t"), new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void aValidTokenForAUserWhoNoLongerExists_authenticatesNobody() throws Exception {
        validToken("t", 99L, "gone@example.com");
        when(userRepository.findRoleById(99L)).thenReturn(Optional.empty());
        MockHttpServletRequest request = requestWithToken("t");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("userId")).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());   // the chain goes on; security then answers 403
    }

    @Test
    void anInvalidOrMissingToken_authenticatesNobody_andNeverTouchesTheDatabase() throws Exception {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(false);

        filter.doFilter(requestWithToken("bad"), new MockHttpServletResponse(), mock(FilterChain.class));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void aDatabaseFailureWhileLookingUpTheRole_authenticatesNobody_ratherThanGuessingARole() throws Exception {
        validToken("t", 7L, "x@example.com");
        when(userRepository.findRoleById(7L)).thenThrow(new IllegalStateException("db down"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = requestWithToken("t");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
    }
}
