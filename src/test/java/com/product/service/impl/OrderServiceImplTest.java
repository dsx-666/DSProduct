package com.product.service.impl;

import com.product.MyException.BusinessException;
import com.product.converter.UserConverter;
import com.product.enums.Role;
import com.product.mapper.OrderMapper;
import com.product.pojo.dto.request.Filter;
import com.product.pojo.dto.response.OrderDetailResponse;
import com.product.pojo.dto.response.OrderPaymentStatsResponse;
import com.product.pojo.po.User;
import com.product.service.ProductService;
import com.product.thread.MyParallelProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private FilterValidator filterValidator;
    @Mock
    private MyParallelProcessor parallelProcessor;
    @Mock
    private ProductService productService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderMapper,
                filterValidator,
                parallelProcessor,
                productService
        );
    }

    @Test
    void shouldReturnPaymentStatsInContractOrder() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        when(orderMapper.selectOrderPaymentStats(
                filter,UserConverter.toScope(user)))
                .thenReturn(List.of(
                        paymentStats(null,1),
                        paymentStats("支付宝支付",1),
                        paymentStats("银行卡支付",2),
                        paymentStats("微信支付",1)
                ));

        OrderPaymentStatsResponse response = orderService
                .selectOrderPaymentStats(filter,user);

        assertEquals(5L,response.getTotalOrderCount());
        assertEquals(4,response.getPayMethodList().size());
        assertEquals("银行卡支付",response.getPayMethodList().get(0).getPayWay());
        assertEquals(new BigDecimal("0.400000"),
                response.getPayMethodList().get(0).getPayRatio());
        assertEquals("微信支付",response.getPayMethodList().get(1).getPayWay());
        assertEquals("支付宝支付",response.getPayMethodList().get(2).getPayWay());
        assertNull(response.getPayMethodList().get(3).getPayWay());
        assertEquals("未知",response.getPayMethodList().get(3).getPayWayLabel());
        assertEquals(new BigDecimal("0.200000"),
                response.getPayMethodList().get(3).getPayRatio());
        assertEquals(List.of("completed"),filter.getStatuses());
    }

    @Test
    void shouldReturnEmptyPaymentStatsWhenThereAreNoOrders() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        when(orderMapper.selectOrderPaymentStats(
                filter,UserConverter.toScope(user)))
                .thenReturn(List.of());

        OrderPaymentStatsResponse response = orderService
                .selectOrderPaymentStats(filter,user);

        assertEquals(0L,response.getTotalOrderCount());
        assertEquals(List.of(),response.getPayMethodList());
    }

    @Test
    void shouldUseManagedBrandScopeForAdmin() {
        Filter filter = createFilter(List.of(),List.of());
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();
        when(orderMapper.selectOrderPaymentStats(
                filter,UserConverter.toScope(user)))
                .thenReturn(List.of(
                        paymentStats("微信支付",1),
                        paymentStats("支付宝支付",1)
                ));

        OrderPaymentStatsResponse response = orderService
                .selectOrderPaymentStats(filter,user);

        assertEquals("BRAND",response.getScope().getMode());
        assertEquals(List.of("NeoTech"),response.getScope().getAllowedBrands());
        assertEquals(2L,response.getTotalOrderCount());
        assertEquals(new BigDecimal("0.500000"),
                response.getPayMethodList().get(0).getPayRatio());
        assertEquals(new BigDecimal("0.500000"),
                response.getPayMethodList().get(1).getPayRatio());
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
                () -> orderService.selectOrderPaymentStats(filter,user)
        );

        assertEquals(403,exception.getCode());
        verify(orderMapper,never()).selectOrderPaymentStats(
                filter,UserConverter.toScope(user)
        );
    }

    @Test
    void shouldReturnFullOrderDetailForSuperAdmin() {
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        when(orderMapper.selectOrderDetail(1001L))
                .thenReturn(orderDetail("320.00"));
        when(orderMapper.selectOrderDetailItemList(1001L,List.of()))
                .thenReturn(List.of(
                        orderItem(1L,"NeoTech","Electronics",2,"100.00","200.00"),
                        orderItem(2L,"Orion","Clothing",2,"50.00","100.00")
                ));

        OrderDetailResponse response = orderService
                .selectOrderDetail(1001L,user);

        assertEquals("FULL",response.getContentScope());
        assertEquals(new BigDecimal("300.00"),response.getAmount());
        assertEquals(new BigDecimal("320.00"),response.getOriginalAmount());
        assertEquals(new BigDecimal("300.00"),response.getItemsTotalAmount());
        assertEquals(new BigDecimal("-20.00"),response.getAmountDifference());
        assertTrue(response.getHasAmountDifference());
        assertEquals(new BigDecimal("300.00"),response.getVisibleAmount());
        assertEquals(4L,response.getVisibleQuantity());
        assertEquals(4L,response.getTotalQuantity());
        assertEquals("ITEM_SUM",response.getAmountSource());
        assertTrue(response.getHasDetails());
        assertEquals(2L,response.getItemCount());
        assertEquals("电子产品",response.getItems().get(0)
                .getProduct().getCategoryLabel());
        assertEquals("服饰",response.getItems().get(1)
                .getProduct().getCategoryLabel());
    }

    @Test
    void shouldReturnOnlyManagedBrandItemsForAdmin() {
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();
        when(orderMapper.selectOrderDetail(1001L))
                .thenReturn(orderDetail("320.00"));
        when(orderMapper.selectOrderDetailItemList(
                1001L,List.of("NeoTech")))
                .thenReturn(List.of(
                        orderItem(1L,"NeoTech","Electronics",2,"100.00","200.00")
                ));

        OrderDetailResponse response = orderService
                .selectOrderDetail(1001L,user);

        assertEquals("BRAND",response.getContentScope());
        assertNull(response.getAmount());
        assertNull(response.getOriginalAmount());
        assertNull(response.getItemsTotalAmount());
        assertNull(response.getAmountDifference());
        assertNull(response.getHasAmountDifference());
        assertEquals(new BigDecimal("200.00"),response.getVisibleAmount());
        assertEquals(2L,response.getVisibleQuantity());
        assertNull(response.getTotalQuantity());
        assertEquals("BRAND_ITEMS",response.getAmountSource());
        assertEquals(1L,response.getItemCount());
        assertEquals("NeoTech",response.getItems().get(0)
                .getProduct().getBrand());
    }

    @Test
    void shouldFallbackToOrderAmountForMissingDetailOrder() {
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        when(orderMapper.selectOrderDetail(1003L))
                .thenReturn(orderDetail("50.00"));
        when(orderMapper.selectOrderDetailItemList(1003L,List.of()))
                .thenReturn(List.of());

        OrderDetailResponse response = orderService
                .selectOrderDetail(1003L,user);

        assertEquals(new BigDecimal("50.00"),response.getAmount());
        assertEquals(new BigDecimal("50.00"),response.getOriginalAmount());
        assertEquals(new BigDecimal("50.00"),response.getVisibleAmount());
        assertEquals("ORDER_TOTAL",response.getAmountSource());
        assertFalse(response.getHasDetails());
        assertEquals(0L,response.getItemCount());
        assertEquals(List.of(),response.getItems());
        assertNull(response.getItemsTotalAmount());
        assertNull(response.getVisibleQuantity());
        assertNull(response.getTotalQuantity());
    }

    @Test
    void shouldHideOrdersWithoutManagedBrandItemsFromAdmin() {
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();
        when(orderMapper.selectOrderDetail(1002L))
                .thenReturn(orderDetail("100.00"));
        when(orderMapper.selectOrderDetailItemList(
                1002L,List.of("NeoTech")))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.selectOrderDetail(1002L,user)
        );

        assertEquals(404,exception.getCode());
    }

    @Test
    void shouldRejectInvalidOrMissingOrderId() {
        User user = User.builder().role(Role.SUPER_ADMIN).build();

        BusinessException invalidIdException = assertThrows(
                BusinessException.class,
                () -> orderService.selectOrderDetail(0L,user)
        );
        assertEquals(20001,invalidIdException.getCode());
        verify(orderMapper,never()).selectOrderDetail(0L);

        when(orderMapper.selectOrderDetail(9999L)).thenReturn(null);
        BusinessException missingOrderException = assertThrows(
                BusinessException.class,
                () -> orderService.selectOrderDetail(9999L,user)
        );
        assertEquals(404,missingOrderException.getCode());
        verify(orderMapper,never()).selectOrderDetailItemList(
                9999L,List.of()
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

    private Map<String,Object> paymentStats(String payWay,long orderCount) {
        Map<String,Object> result = new HashMap<>();
        result.put("payWay",payWay);
        result.put("orderCount",orderCount);
        return result;
    }

    private Map<String,Object> orderDetail(String originalAmount) {
        Map<String,Object> result = new HashMap<>();
        result.put("orderId",1001L);
        result.put("status","completed");
        result.put("createTime",LocalDateTime.of(2025,11,12,10,0));
        result.put("userId",101L);
        result.put("userName","演示用户甲");
        result.put("payWay","微信支付");
        result.put("originalAmount",new BigDecimal(originalAmount));
        return result;
    }

    private Map<String,Object> orderItem(
            long orderItemId,
            String brand,
            String category,
            long quantity,
            String itemPrice,
            String itemTotal) {
        Map<String,Object> result = new HashMap<>();
        result.put("orderItemId",orderItemId);
        result.put("quantity",quantity);
        result.put("itemPrice",new BigDecimal(itemPrice));
        result.put("itemTotal",new BigDecimal(itemTotal));
        result.put("productId",orderItemId);
        result.put("productName","演示商品"+orderItemId);
        result.put("category",category);
        result.put("brand",brand);
        result.put("currentPrice",new BigDecimal(itemPrice));
        result.put("rating",new BigDecimal("4.8"));
        return result;
    }
}
