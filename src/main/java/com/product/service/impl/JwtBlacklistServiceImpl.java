package com.product.service.impl;

import com.product.pojo.other.JwtUtil;
import com.product.service.JwtBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@RequiredArgsConstructor
@Service
public class JwtBlacklistServiceImpl implements JwtBlacklistService {
    private static final String JWT_BLACKLIST_KEY = "jwt:blacklist:";
    private static final String LOGOUT_VALUE = "logout";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;

    @Override
    public void addBlacklist(String jwtToken) {
        String redisKey = getRedisKey(jwtToken);
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))){
            return;
        }
        Claims claims = jwtUtil.parseToken(jwtToken);
        Duration expireTime = Duration.between(
                Instant.now(),claims.getExpiration().toInstant()
        );
        if(expireTime.isZero() || expireTime.isNegative()){
            return;
        }
        stringRedisTemplate.opsForValue().set(
                redisKey,LOGOUT_VALUE,expireTime
        );
    }

    @Override
    public boolean isBlacklisted(String jwtToken) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(getRedisKey(jwtToken))
        );
    }

    private String getRedisKey(String jwtToken) {
        try{
            // 得到签名算法
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            // 得到bit数组
            byte[] tokenHash = messageDigest.digest(
                    jwtToken.getBytes(StandardCharsets.UTF_8)
            );
            // 转化成string类型
            return JWT_BLACKLIST_KEY + HexFormat.of().formatHex(tokenHash);
        }catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("无法生成JWT黑名单标识",e);
        }
    }
}
