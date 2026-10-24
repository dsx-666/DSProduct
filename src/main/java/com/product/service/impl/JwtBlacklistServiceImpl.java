package com.product.service.impl;

import com.product.pojo.other.JwtUtil;
import com.product.service.JwtBlacklistService;
import com.product.service.RedisService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
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

    private final RedisService redisService;
    private final JwtUtil jwtUtil;

    // 用户退出时把当前令牌放进黑名单，保存时间只到令牌自然过期。
    @Override
    public void addBlacklist(String jwtToken) {
        String redisKey = getRedisKey(jwtToken);
        if(redisService.hasKey(redisKey)){
            return;
        }
        Claims claims = jwtUtil.parseToken(jwtToken);
        Duration expireTime = Duration.between(
                Instant.now(),claims.getExpiration().toInstant()
        );
        if(expireTime.isZero() || expireTime.isNegative()){
            return;
        }
        redisService.setString(redisKey,LOGOUT_VALUE,expireTime);
    }

    // 每次鉴权时检查令牌是否已经被退出操作作废。
    @Override
    public boolean isBlacklisted(String jwtToken) {
        return redisService.hasKey(getRedisKey(jwtToken));
    }

    // 对令牌做 SHA-256 后再当 Redis key，避免把完整 JWT 暴露在缓存里。
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
