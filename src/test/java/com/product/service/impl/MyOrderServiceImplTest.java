package com.product.service.impl;

import com.product.MyException.ApiHttpException;
import com.product.enums.Role;
import com.product.mapper.OrderMapper;
import com.product.pojo.bo.MyOrderListRow;
import com.product.pojo.dto.request.MyOrderListRequest;
import com.product.pojo.dto.response.MyOrderListResponse;
import com.product.pojo.dto.response.OrderDetailResponse;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyOrderServiceImplTest {
    @Mock OrderMapper orderMapper;
    @Mock FilterValidator filterValidator;
    @Mock MyParallelProcessor parallelProcessor;
    @Mock ProductService productService;

    private OrderServiceImpl orderService;
    private User user;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderMapper,filterValidator,parallelProcessor,productService
        );
        user = User.builder().userId(101L).role(Role.NORMAL_USER).build();
    }

    @Test
    void shouldDefaultToFullBusinessRangeAndAllStatuses() {
        MyOrderListRequest request = MyOrderListRequest.builder()
                .page(1L).num(20L).keyWord("  演示  ").build();
        MyOrderListRow row = MyOrderListRow.builder()
                .orderId(1001L)
                .status("completed")
                .createTime(LocalDateTime.of(2025,11,12,10,0))
                .userId(101L)
                .userName("演示用户甲")
                .payWay("微信支付")
                .originalAmount(new BigDecimal("320"))
                .itemsTotalAmount(new BigDecimal("300"))
                .detailCount(2L)
                .productCount(2L)
                .productSummary("演示耳机、演示服饰")
                .build();
        when(orderMapper.selectMyOrderList(request,101L))
                .thenReturn(List.of(row));
        when(orderMapper.selectMyOrderListCount(request,101L))
                .thenReturn(1L);

        MyOrderListResponse response = orderService
                .selectMyOrderList(request,user);

        assertEquals(LocalDate.of(2024,1,1),request.getStartTime());
        assertEquals(LocalDate.of(2025,11,14),request.getEndTime());
        assertEquals(List.of("processing","shipped","completed",
                "cancelled","returned"),request.getStatuses());
        assertEquals("演示",response.getKeyWord());
        assertEquals("OWN",response.getScope().getMode());
        assertEquals(new BigDecimal("300.00"),
                response.getOrderList().get(0).getAmount());
        assertEquals(new BigDecimal("300.00"),
                response.getOrderList().get(0).getMatchedAmount());
        assertTrue(response.getOrderList().get(0).getHasAmountDifference());
    }

    @Test
    void shouldReturnNotFoundWithoutLoadingItemsForAnotherUsersOrder() {
        when(orderMapper.selectMyOrderDetail(1002L,101L)).thenReturn(null);

        ApiHttpException exception = assertThrows(
                ApiHttpException.class,
                () -> orderService.selectMyOrderDetail(1002L,user)
        );

        assertEquals(404,exception.getHttpStatus().value());
        verify(orderMapper,never()).selectOrderDetailItemList(
                1002L,List.of()
        );
    }

    @Test
    void shouldReturnOwnDetailWithOwnContentScope() {
        Map<String,Object> detail = new HashMap<>();
        detail.put("orderId",1001L);
        detail.put("status","completed");
        detail.put("createTime",LocalDateTime.of(2025,11,12,10,0));
        detail.put("userId",101L);
        detail.put("userName","演示用户甲");
        detail.put("payWay","微信支付");
        detail.put("originalAmount",new BigDecimal("50"));
        when(orderMapper.selectMyOrderDetail(1001L,101L)).thenReturn(detail);
        when(orderMapper.selectOrderDetailItemList(1001L,List.of()))
                .thenReturn(List.of());

        OrderDetailResponse response = orderService
                .selectMyOrderDetail(1001L,user);

        assertEquals("OWN",response.getContentScope());
        assertEquals(new BigDecimal("50.00"),response.getAmount());
        assertEquals("ORDER_TOTAL",response.getAmountSource());
        assertNull(response.getVisibleQuantity());
        assertEquals(List.of(),response.getItems());
    }
}
