package com.product.config;

import com.product.security.exception.MyAuthenticationEntryPoint;
import com.product.security.filter.JwtAuthenticationFilter;
import com.product.security.filter.PasswordAuthenticationFilter;
import com.product.security.provider.PasswordAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SpringSecurityConfig {
    // 认证管理器，可以通过依赖注入来加入provide
    @Bean
    public AuthenticationManager authenticationManager(
            PasswordAuthenticationProvider passwordAuthenticationProvider)
            throws Exception {
        return new ProviderManager(passwordAuthenticationProvider);
    }

    @Bean
    // 构造链路（chain）
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            MyAuthenticationEntryPoint entryPoint,
            JwtAuthenticationFilter jwtFilter
    ) throws Exception {
        PasswordAuthenticationFilter filter = new
                PasswordAuthenticationFilter();
        // 必须把这个filter对应的manager设置，他不会自动获取这个
        filter.setAuthenticationManager(authenticationManager);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth ->
                        auth
                        .requestMatchers("/user/**").permitAll()
                        .anyRequest().authenticated()
                )
                // 当前过滤链使用这个 AuthenticationManager 作为认证管理器
                .authenticationManager(authenticationManager)
                // 必须把这个Filter加到链路里面，这个Filter会getManage来获取你定义的manager
                .addFilterAt(
                        filter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                // 异常捕获并且处理
                .exceptionHandling(
                        exception ->
                        exception.authenticationEntryPoint(entryPoint)
                        // 注册你的入口点
                );


        return http.build();
    }


}
