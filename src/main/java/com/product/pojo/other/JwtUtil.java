package com.product.pojo.other;

import com.product.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
@Data
@RequiredArgsConstructor
@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;


    /**
     * 生成 JWT
     */
    public String generateToken(Long userId, String username) {

        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpire());

        return Jwts.builder()
                // 设置 JWT 的 "sub" 字段，通常放用户ID
                .subject(String.valueOf(userId))
                // 自定义字段，把用户名也塞进 payload
                .claim("username", username)
                // 设置签发时间（iat）
                .issuedAt(now)
                // 设置过期时间（exp）
                .expiration(expiration)
                // 用密钥签名，防止被篡改
                .signWith(jwtProperties.getKey())
                // 最终生成 "xxx.yyy.zzz" 格式的 Token 字符串
                .compact();


    }
    /**
     * 解析 JWT
     */
    public Claims parseToken(String token) {

        return Jwts.parser()
                // 设置验签密钥，用于验证签名是否合法
                .verifyWith(jwtProperties.getKey())
                // 构建 JWT 解析器实例
                .build()
                // 解析 Token 字符串，并自动验签和校验过期时间
                .parseSignedClaims(token)
                // 获取解析后的 Payload（载荷）内容
                .getPayload();
    }
    /**
     * 获取 userId
     */
    public Long getUserId(String token) {

        Claims claims = this.parseToken(token);
        // claim可以直接get一些默认的参数
        return Long.valueOf(claims.getSubject());
    }


    /**
     * 获取 username
     */
    public String getUsername(String token) {

        Claims claims = this.parseToken(token);
        // 为啥要加String.class是因为默认返回object类
        return claims.get("username", String.class);
    }


    /**
     * 判断 Token 是否有效
     */
    public boolean isValid(String token) {

        try {
            this.parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}