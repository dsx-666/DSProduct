package com.product.service.impl;

import com.product.enums.Role;
import com.product.mapper.ProductMapper;
import com.product.pojo.dto.response.ProductBrandResponse;
import com.product.pojo.po.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductBrandServiceImplTest {
    @Mock
    private ProductMapper productMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private FilterValidator filterValidator;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productMapper,stringRedisTemplate,filterValidator
        );
    }

    @Test
    void shouldReturnActualDistinctBrandsInAscendingOrderForSuperAdmin() {
        when(productMapper.selectBrands()).thenReturn(Arrays.asList(
                "Zenith","Acme","NeoTech","Acme",null
        ));
        User user = User.builder().role(Role.SUPER_ADMIN).build();

        ProductBrandResponse response = productService.getBrands(user);

        assertEquals(List.of("Acme","NeoTech","Zenith"),response.getBrands()
                .stream()
                .map(ProductBrandResponse.BrandOption::getValue)
                .toList());
        assertEquals(List.of("Acme","NeoTech","Zenith"),response.getBrands()
                .stream()
                .map(ProductBrandResponse.BrandOption::getLabel)
                .toList());
        assertEquals("ALL",response.getScope().getMode());
        assertEquals(List.of(),response.getScope().getAllowedBrands());
        assertTrue(response.getScope().getCanViewFullOrder());
        assertTrue(response.getScope().getCanManageUsers());
    }

    @Test
    void shouldReturnOnlyManagedExistingBrandForAdmin() {
        when(productMapper.selectBrands()).thenReturn(List.of(
                "Acme","NeoTech","Orion"
        ));
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();

        ProductBrandResponse response = productService.getBrands(user);

        assertEquals(1,response.getBrands().size());
        assertEquals("NeoTech",response.getBrands().get(0).getValue());
        assertEquals("NeoTech",response.getBrands().get(0).getLabel());
        assertEquals("BRAND",response.getScope().getMode());
        assertEquals(List.of("NeoTech"),response.getScope().getAllowedBrands());
        assertFalse(response.getScope().getCanViewFullOrder());
        assertFalse(response.getScope().getCanManageUsers());
    }

    @Test
    void shouldReturnEmptyScopeForUnassignedOrMissingAdminBrand() {
        when(productMapper.selectBrands()).thenReturn(List.of(
                "Acme","NeoTech","Orion"
        ));

        ProductBrandResponse unassignedResponse = productService.getBrands(
                User.builder().role(Role.ADMIN).brand(null).build()
        );
        ProductBrandResponse missingResponse = productService.getBrands(
                User.builder().role(Role.ADMIN).brand("RemovedBrand").build()
        );

        assertEquals(List.of(),unassignedResponse.getBrands());
        assertEquals(List.of(),unassignedResponse.getScope().getAllowedBrands());
        assertEquals(List.of(),missingResponse.getBrands());
        assertEquals(List.of(),missingResponse.getScope().getAllowedBrands());
    }
}
