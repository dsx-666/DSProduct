package com.product.security.filter;
import com.product.pojo.other.JwtUtil;
import com.product.security.token.PasswordAuthenticationToken;
import com.product.service.impl.UserDetailsServiceImpl;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;


@Component
@RequiredArgsConstructor
//@NoArgsConstructor
// OncePerRequestFilter用于过滤器必须会经过这个过滤器
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        try{
            String header =  request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }
            String jwtToken = header.substring(7);
            if(!jwtUtil.isValid(jwtToken)) {
                filterChain.doFilter(request, response);
                return;
            }
            String username =  jwtUtil.getUsername(jwtToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            Authentication authentication =
                    new PasswordAuthenticationToken(
                            userDetails,
                            jwtToken,
                            userDetails.getAuthorities()
                    );
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            // 包装成 AuthenticationException 的子类，向上抛出
            throw new AuthenticationServiceException("Token已过期", e);

        } catch (MalformedJwtException | SignatureException e) {
            // 同样包装后抛出
            throw new AuthenticationServiceException("Token无效", e);
        }
    }
}
