package com.product.controller;

import com.product.MyException.BusinessException;
import com.product.converter.UserConverter;
import com.product.enums.Role;
import com.product.pojo.dto.response.ProductBrandResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.po.User;
import com.product.pojo.vo.ProductBrandVo;
import com.product.pojo.vo.Result;
import com.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductBrandControllerTest {
    @Test
    void shouldRejectUnsupportedQueryParameters() {
        ProductService productService = mock(ProductService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ProductController controller = new ProductController(productService);
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        MyUserDetails userDetails = MyUserDetails.builder().user(user).build();
        when(request.getParameterMap()).thenReturn(Map.of(
                "brand",new String[]{"NeoTech"}
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.getBrands(userDetails,request)
        );

        assertEquals(20001,exception.getCode());
        verify(productService,never()).getBrands(user);
    }

    @Test
    void shouldReturnBrandOptionsAndScope() {
        ProductService productService = mock(ProductService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ProductController controller = new ProductController(productService);
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();
        MyUserDetails userDetails = MyUserDetails.builder().user(user).build();
        when(request.getParameterMap()).thenReturn(Map.of());
        when(productService.getBrands(user)).thenReturn(
                ProductBrandResponse.builder()
                        .brands(List.of(ProductBrandResponse.BrandOption.builder()
                                .value("NeoTech")
                                .label("NeoTech")
                                .build()))
                        .scope(UserConverter.toScope(user))
                        .build()
        );

        Result<ProductBrandVo> result = controller.getBrands(userDetails,request);

        assertEquals(200,result.getCode());
        assertEquals(1,result.getData().getBrands().size());
        assertEquals("NeoTech",result.getData().getBrands().get(0).getValue());
        assertEquals("BRAND",result.getData().getScope().getMode());
    }
}
