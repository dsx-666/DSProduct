package com.product.security.provider;

import com.product.security.token.PasswordAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// TODO:异常处理
// 被manager调度
@Slf4j
@RequiredArgsConstructor
@Component
public class PasswordAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;

    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException
    {
        // 接受到的参数自定义的MyUserDetails
        UserDetails myUserDetails = userDetailsService.loadUserByUsername(
                authentication
                        .getPrincipal()
                        .toString());
        String password = myUserDetails.getPassword();

        if (
                !passwordEncoder.matches(authentication
                .getCredentials()
                .toString(),
                password)
        ) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        return new PasswordAuthenticationToken(
                myUserDetails,
                password,
                myUserDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
