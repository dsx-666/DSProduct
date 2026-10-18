package com.product.security.filter;

import com.product.pojo.other.JwtUtil;
import com.product.security.token.PasswordAuthenticationToken;
import com.product.service.JwtBlacklistService;
import com.product.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private JwtBlacklistService jwtBlacklistService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtUtil,userDetailsService,jwtBlacklistService
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectBlacklistedTokenForBusinessRequest() throws Exception {
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer jwt-token");
        when(jwtBlacklistService.isBlacklisted("jwt-token"))
                .thenReturn(true);
        when(request.getMethod()).thenReturn("GET");

        jwtAuthenticationFilter.doFilterInternal(
                request,response,filterChain
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService,never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request,response);
    }

    @Test
    void shouldAllowRepeatedLogoutWithoutAuthenticatingUserAgain()
            throws Exception {
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer jwt-token");
        when(jwtBlacklistService.isBlacklisted("jwt-token"))
                .thenReturn(true);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/api/auth/logout");

        jwtAuthenticationFilter.doFilterInternal(
                request,response,filterChain
        );

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication instanceof PasswordAuthenticationToken);
        verify(userDetailsService,never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request,response);
    }
}
