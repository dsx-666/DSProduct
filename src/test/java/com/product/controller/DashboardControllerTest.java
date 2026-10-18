package com.product.controller;

import com.product.MyException.BusinessException;
import com.product.enums.Role;
import com.product.pojo.dto.response.DashboardMetaResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.po.User;
import com.product.pojo.vo.Result;
import com.product.pojo.vo.DashboardMetaVo;
import com.product.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerTest {
    @Test
    void shouldRejectUnsupportedQueryParameters() {
        DashboardService dashboardService = mock(DashboardService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        DashboardController controller = new DashboardController(dashboardService);
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        MyUserDetails userDetails = MyUserDetails.builder().user(user).build();
        when(request.getParameterMap()).thenReturn(Map.of(
                "year",new String[]{"2025"}
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.getDashboardMeta(userDetails,request)
        );

        assertEquals(20001,exception.getCode());
        verify(dashboardService,never()).getDashboardMeta(user);
    }

    @Test
    void shouldReturnDashboardMetaVo() {
        DashboardService dashboardService = mock(DashboardService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        DashboardController controller = new DashboardController(dashboardService);
        User user = User.builder().role(Role.SUPER_ADMIN).build();
        MyUserDetails userDetails = MyUserDetails.builder().user(user).build();
        when(request.getParameterMap()).thenReturn(Map.of());
        when(dashboardService.getDashboardMeta(user))
                .thenReturn(metaResponse());

        Result<DashboardMetaVo> result = controller
                .getDashboardMeta(userDetails,request);

        assertEquals(200,result.getCode());
        assertEquals(LocalDate.of(2025,11,14),result.getData().getBusinessDate());
        assertEquals("Asia/Shanghai",result.getData().getTimeZone());
        assertEquals(List.of(7L,30L,90L),result.getData().getQuickRangeDays());
        assertEquals("ALL",result.getData().getScope().getMode());
    }

    private DashboardMetaResponse metaResponse() {
        return DashboardMetaResponse.builder()
                .businessDate(LocalDate.of(2025,11,14))
                .timeZone("Asia/Shanghai")
                .dateRange(DashboardMetaResponse.DateRange.builder()
                        .minDate(LocalDate.of(2024,1,1))
                        .maxDate(LocalDate.of(2025,11,14))
                        .build())
                .defaultDateRange(DashboardMetaResponse.DefaultDateRange.builder()
                        .startTime(LocalDate.of(2025,10,16))
                        .endTime(LocalDate.of(2025,11,14))
                        .build())
                .defaultFilters(DashboardMetaResponse.DefaultFilters.builder()
                        .category(List.of())
                        .brand(List.of())
                        .statuses(List.of("completed"))
                        .keyWord("")
                        .metric("amount")
                        .granularity("day")
                        .page(1L)
                        .num(20L)
                        .build())
                .orderStatusOptions(List.of())
                .rankingMetricOptions(List.of())
                .granularityOptions(List.of())
                .quickRangeDays(List.of(7L,30L,90L))
                .pageSizeOptions(List.of(10L,20L,50L,100L))
                .scope(com.product.converter.UserConverter.toScope(
                        User.builder().role(Role.SUPER_ADMIN).build()))
                .build();
    }
}
