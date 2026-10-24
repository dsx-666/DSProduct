package com.product.service.impl;

import com.product.MyException.BusinessException;
import com.product.enums.Role;
import com.product.mapper.ProductMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.response.ProductCategoryStatsResponse;
import com.product.pojo.po.User;
import com.product.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {
    @Mock
    private ProductMapper productMapper;
    @Mock
    private RedisService redisService;
    @Mock
    private FilterValidator filterValidator;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productMapper,redisService,filterValidator
        );
    }

    @Test
    void shouldReturnCategoryStatsAndUncategorizedOrdersForSuperAdmin() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder().role(Role.SUPER_ADMIN).build();

        when(productMapper.selectProductCategoryStats(filter,
                com.product.converter.UserConverter.toScope(user)))
                .thenReturn(List.of(
                        categoryStats("Clothing","100.00",2,1),
                        categoryStats("Electronics","300.00",3,2)
                ));
        when(productMapper.selectUncategorizedStats(filter))
                .thenReturn(uncategorizedStats("50.00",1));

        ProductCategoryStatsResponse response = productService
                .getProductCategoryStats(filter,user);

        assertEquals(new BigDecimal("450.00"),response.getTotalSalesAmount());
        assertEquals(3,response.getCategoryList().size());
        assertEquals("Electronics",response.getCategoryList().get(0).getCategory());
        assertEquals(new BigDecimal("0.666667"),
                response.getCategoryList().get(0).getSalesRatio());
        assertNull(response.getCategoryList().get(2).getCategory());
        assertNull(response.getCategoryList().get(2).getSalesVolume());
        assertEquals(new BigDecimal("0.111111"),
                response.getCategoryList().get(2).getSalesRatio());
        assertEquals(List.of("completed"),filter.getStatuses());
    }

    @Test
    void shouldLimitAdminToManagedBrandAndExcludeUncategorizedOrders() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();

        when(productMapper.selectProductCategoryStats(filter,
                com.product.converter.UserConverter.toScope(user)))
                .thenReturn(List.of(
                        categoryStats("Electronics","300.00",3,2)
                ));

        ProductCategoryStatsResponse response = productService
                .getProductCategoryStats(filter,user);

        assertEquals(new BigDecimal("300.00"),response.getTotalSalesAmount());
        assertEquals(1,response.getCategoryList().size());
        assertEquals(BigDecimal.ONE.setScale(6),
                response.getCategoryList().get(0).getSalesRatio());
        assertEquals("BRAND",response.getScope().getMode());
        assertEquals(List.of("NeoTech"),response.getScope().getAllowedBrands());
        assertFalse(response.getScope().getCanViewFullOrder());
        verify(productMapper,never()).selectUncategorizedStats(filter);
    }

    @Test
    void shouldRejectBrandOutsideAdminScope() {
        Filter filter = createFilter(List.of(),List.of("Orion"));
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.getProductCategoryStats(filter,user)
        );

        assertEquals(403,exception.getCode());
        verify(productMapper,never()).selectProductCategoryStats(
                filter,com.product.converter.UserConverter.toScope(user)
        );
    }

    private Filter createFilter(List<String> categories,List<String> brands) {
        return Filter.builder()
                .startTime(LocalDate.of(2025,11,12))
                .endTime(LocalDate.of(2025,11,14))
                .categories(categories)
                .brands(brands)
                .statuses(List.of())
                .build();
    }

    private Map<String,Object> categoryStats(
            String category,String salesAmount,long salesVolume,long uniqueOrderCount) {
        Map<String,Object> result = new HashMap<>();
        result.put("category",category);
        result.put("salesAmount",new BigDecimal(salesAmount));
        result.put("salesVolume",salesVolume);
        result.put("uniqueOrderCount",uniqueOrderCount);
        return result;
    }

    private Map<String,Object> uncategorizedStats(
            String salesAmount,long uniqueOrderCount) {
        Map<String,Object> result = new HashMap<>();
        result.put("salesAmount",new BigDecimal(salesAmount));
        result.put("uniqueOrderCount",uniqueOrderCount);
        return result;
    }
}
