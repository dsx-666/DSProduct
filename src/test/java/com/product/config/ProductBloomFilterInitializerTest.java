package com.product.config;

import com.product.mapper.BrandBusinessMapper;
import com.product.service.RedisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductBloomFilterInitializerTest {
    @Mock RedisService redisService;
    @Mock BrandBusinessMapper brandBusinessMapper;

    // 验证启动预热会把数据库中的全部商品 ID 交给布隆过滤器。
    @Test
    void shouldPreloadAllProductIdsWhenApplicationStarts() throws Exception {
        when(brandBusinessMapper.selectAllProductIds()).thenReturn(List.of(1L, 2L, 3L));
        doAnswer(invocation -> {
            Supplier<?> loader = invocation.getArgument(1);
            assertEquals(List.of(1L, 2L, 3L), loader.get());
            return null;
        }).when(redisService).initializeBloomFilter(eq("product"), any());

        new ProductBloomFilterInitializer(redisService, brandBusinessMapper).run(null);

        verify(redisService).initializeBloomFilter(eq("product"), any());
    }

    // 验证数据库暂时不可用时不会阻止整个应用启动。
    @Test
    void shouldKeepStartingWhenBloomWarmupFails() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(redisService).initializeBloomFilter(eq("product"), any());

        ProductBloomFilterInitializer initializer =
                new ProductBloomFilterInitializer(redisService, brandBusinessMapper);

        assertDoesNotThrow(() -> initializer.run(null));
    }
}
