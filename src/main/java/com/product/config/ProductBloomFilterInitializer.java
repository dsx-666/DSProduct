package com.product.config;

import com.product.mapper.BrandBusinessMapper;
import com.product.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductBloomFilterInitializer implements ApplicationRunner {
    private static final String PRODUCT_BLOOM_FILTER = "product";

    private final RedisService redisService;
    private final BrandBusinessMapper brandBusinessMapper;

    // 应用启动阶段预热全部商品 ID，失败时保留查询阶段的懒加载兜底。
    @Override
    public void run(ApplicationArguments args) {
        try {
            redisService.initializeBloomFilter(
                    PRODUCT_BLOOM_FILTER,
                    brandBusinessMapper::selectAllProductIds
            );
            log.info("商品布隆过滤器预热完成");
        } catch(RuntimeException e) {
            log.warn("商品布隆过滤器预热失败，将在首次查询时重试", e);
        }
    }
}
