package com.product.security.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
// 异常过滤器，这个不会主动拦截请求，而是被动地被触发。触发条件是：过滤器链中某个环节抛出了 AuthenticationException
@Component
public class MyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        String message;
        if (authException instanceof AuthenticationServiceException) {
            // 数据库异常等系统级错误
            message = "系统繁忙，请稍后重试";
        } else {
            // 用户名密码错误、Token 无效等
            message = authException.getMessage()+"请先登录";
        }

        response.getWriter().write("""
            {
                "code": 401,
                "message": "%s",
                "data": null
            }
            """.formatted(message));
    }
}
