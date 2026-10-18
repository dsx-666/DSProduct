package com.product.service;

public interface JwtBlacklistService {
    void addBlacklist(String jwtToken);
    boolean isBlacklisted(String jwtToken);
}
