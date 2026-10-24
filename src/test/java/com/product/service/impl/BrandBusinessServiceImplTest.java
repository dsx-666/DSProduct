package com.product.service.impl;

import com.product.MyException.ApiHttpException;
import com.product.config.BusinessProperties;
import com.product.enums.Role;
import com.product.mapper.BrandBusinessMapper;
import com.product.mapper.ProductMapper;
import com.product.pojo.po.User;
import com.product.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandBusinessServiceImplTest {
    @Mock BrandBusinessMapper mapper;
    @Mock ProductMapper productMapper;
    @Mock RedisService redisService;
    private BrandBusinessServiceImpl service;

    @BeforeEach
    void setUp() {
        BusinessProperties properties = new BusinessProperties();
        properties.setStartTime(LocalDate.of(2024, 1, 1));
        properties.setTime(LocalDate.of(2025, 11, 14));
        service = new BrandBusinessServiceImpl(mapper, productMapper, properties,
                redisService);
    }

    @Test
    void unassignedAdminGetsEmptyProductPageWithoutDatabaseQuery() {
        User admin = User.builder().role(Role.ADMIN).build();

        Map<String,Object> result = service.getProducts(admin, 1, 20, "FILTERED",
                null, null, null, null, "salesVolume", "desc");

        assertEquals(0L, result.get("total"));
        assertEquals(List.of(), result.get("productList"));
        verifyNoInteractions(mapper);
    }

    @Test
    void adminCannotSupplyBrandFilter() {
        User admin = User.builder().role(Role.ADMIN).brand("NeoTech").build();

        ApiHttpException exception = assertThrows(ApiHttpException.class,
                () -> service.getProducts(admin, 1, 20, "ALL", null, null,
                        List.of("NeoTech"), null, "salesVolume", "desc"));

        assertEquals(400, exception.getHttpStatus().value());
    }

    @Test
    void allRangeRejectsDates() {
        User superAdmin = User.builder().role(Role.SUPER_ADMIN).build();

        ApiHttpException exception = assertThrows(ApiHttpException.class,
                () -> service.getProducts(superAdmin, 1, 20, "ALL",
                        LocalDate.of(2025, 1, 1), null, null,
                        null, "salesVolume", "desc"));

        assertEquals(400, exception.getHttpStatus().value());
    }

    @Test
    void productListUsesStableScopeAndCalculatesRepurchaseRate() {
        User admin = User.builder().role(Role.ADMIN).brand("NeoTech").build();
        when(mapper.selectBrandProducts(eq(List.of("NeoTech")), isNull(), isNull(), isNull(),
                eq("salesVolume"), eq("desc"), eq(0L), eq(20)))
                .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of(
                        "productId", 1L, "salesVolume", 3L, "salesAmount", 300,
                        "repurchaseRate", new java.math.BigDecimal("0.500000")))));
        when(mapper.countBrandProducts(List.of("NeoTech"), null)).thenReturn(1L);

        Map<String,Object> result = service.getProducts(admin, 1, 20, "ALL",
                null, null, null, null, "salesVolume", "desc");

        assertEquals(1L, result.get("total"));
        Map<?,?> product = (Map<?,?>)((List<?>)result.get("productList")).get(0);
        assertEquals(new java.math.BigDecimal("0.500000"), product.get("repurchaseRate"));
        assertEquals("AVAILABLE", ((Map<?,?>)product.get("metricStatus")).get("repurchaseRate"));
    }

    @Test
    void productDetailReturnsEventAndCurrentAfterSaleMetrics() {
        User admin = User.builder().role(Role.ADMIN).brand("NeoTech").build();
        when(redisService.mightContain("product", 1L)).thenReturn(true);
        when(mapper.selectProduct(1L)).thenReturn(new java.util.LinkedHashMap<>(Map.of(
                "productId", 1L, "productName", "耳机", "brand", "NeoTech",
                "category", "Electronics", "currentPrice", 110, "rating", 4.8)));
        when(redisService.getOrLoadWithMutex(anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<?>)invocation.getArgument(4)).get());
        when(mapper.selectProductSales(1L, null, null)).thenReturn(Map.of(
                "salesVolume", 3L, "salesAmount", new BigDecimal("300.00"), "buyerCount", 2L));
        when(mapper.selectProductAfterSaleRates(1L, null, null)).thenReturn(Map.of(
                "returnRate", new BigDecimal("0.100000"),
                "cancelRate", new BigDecimal("0.200000")));
        when(mapper.selectProductEventMetrics(1L, null, null)).thenReturn(Map.of(
                "viewCount", 25L, "cartUserCount", 6L, "wishlistUserCount", 4L));
        when(mapper.selectReviewStats(1L, null, null)).thenReturn(Map.of("reviewCount", 0L));
        when(mapper.selectProductGender(1L, null, null)).thenReturn(List.of());
        when(mapper.selectProductCustomerSegments(1L, null, null,
                LocalDate.of(2025, 11, 14))).thenReturn(List.of());
        when(mapper.selectReviewPreview(1L, null, null, 0, 5)).thenReturn(List.of());

        Map<String,Object> result = service.getProductDetail(admin, 1L, "ALL", null, null);

        Map<?,?> metrics = (Map<?,?>)result.get("metrics");
        assertEquals(25L, metrics.get("viewCount"));
        assertEquals(6L, metrics.get("cartUserCount"));
        assertEquals(4L, metrics.get("wishlistUserCount"));
        assertEquals(new BigDecimal("0.100000"), metrics.get("returnRate"));
        assertEquals(new BigDecimal("0.200000"), metrics.get("cancelRate"));
        Map<?,?> status = (Map<?,?>)result.get("metricStatus");
        assertEquals("AVAILABLE", status.get("viewCount"));
        assertEquals("AVAILABLE", status.get("returnRate"));
    }
}
