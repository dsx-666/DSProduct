package com.product.security.filter;

import com.product.security.token.PasswordAuthenticationToken;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

// 这个是专用型过滤器专门用于过滤特定路由而OncePerRequestFilter无论哪个路由都要经过一次
// 密码过滤器用于过滤密码登录接口
// 过滤器不需要加入bean容器管理
public class PasswordAuthenticationFilter
        extends AbstractAuthenticationProcessingFilter {

    public PasswordAuthenticationFilter() {
        super("/login");
    }


    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response)
            throws AuthenticationException, ServletException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        PasswordAuthenticationToken token =
                new PasswordAuthenticationToken(
                        username,
                        password
                );

        return getAuthenticationManager()
                .authenticate(token);
    }

}