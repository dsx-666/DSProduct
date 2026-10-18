package com.product.service.impl;

import com.product.config.BusinessProperties;
import com.product.enums.Role;
import com.product.pojo.dto.response.DashboardMetaResponse;
import com.product.pojo.po.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceImplTest {
    private BusinessProperties businessProperties;
    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        businessProperties = new BusinessProperties();
        businessProperties.setTime(LocalDate.of(2025,11,14));
        businessProperties.setStartTime(LocalDate.of(2024,1,1));
        dashboardService = new DashboardServiceImpl(businessProperties);
    }

    @Test
    void shouldReturnContractMetaForSuperAdmin() {
        User user = User.builder().role(Role.SUPER_ADMIN).build();

        DashboardMetaResponse response = dashboardService
                .getDashboardMeta(user);

        assertEquals(LocalDate.of(2025,11,14),response.getBusinessDate());
        assertEquals("Asia/Shanghai",response.getTimeZone());
        assertEquals(LocalDate.of(2024,1,1),response.getDateRange().getMinDate());
        assertEquals(LocalDate.of(2025,11,14),response.getDateRange().getMaxDate());
        assertEquals(LocalDate.of(2025,10,16),response
                .getDefaultDateRange().getStartTime());
        assertEquals(LocalDate.of(2025,11,14),response
                .getDefaultDateRange().getEndTime());
        assertEquals(List.of(),response.getDefaultFilters().getCategory());
        assertEquals(List.of(),response.getDefaultFilters().getBrand());
        assertEquals(List.of("completed"),response
                .getDefaultFilters().getStatuses());
        assertEquals("",response.getDefaultFilters().getKeyWord());
        assertEquals("amount",response.getDefaultFilters().getMetric());
        assertEquals("day",response.getDefaultFilters().getGranularity());
        assertEquals(1L,response.getDefaultFilters().getPage());
        assertEquals(20L,response.getDefaultFilters().getNum());
        assertEquals(List.of("processing","shipped","completed","cancelled","returned"),
                response.getOrderStatusOptions().stream()
                        .map(DashboardMetaResponse.Option::getValue)
                        .toList());
        assertEquals("已退货",response.getOrderStatusOptions().get(4).getLabel());
        assertEquals(List.of("amount","quantity"),response
                .getRankingMetricOptions().stream()
                .map(DashboardMetaResponse.Option::getValue)
                .toList());
        assertEquals(List.of("day","week","month"),response
                .getGranularityOptions().stream()
                .map(DashboardMetaResponse.Option::getValue)
                .toList());
        assertEquals(List.of(7L,30L,90L),response.getQuickRangeDays());
        assertEquals(List.of(10L,20L,50L,100L),response.getPageSizeOptions());
        assertEquals("ALL",response.getScope().getMode());
        assertTrue(response.getScope().getCanViewFullOrder());
        assertTrue(response.getScope().getCanManageUsers());
    }

    @Test
    void shouldReturnBrandScopeForAdminWithoutChangingDefaultFilters() {
        User user = User.builder()
                .role(Role.ADMIN)
                .brand("NeoTech")
                .build();

        DashboardMetaResponse response = dashboardService
                .getDashboardMeta(user);

        assertEquals("BRAND",response.getScope().getMode());
        assertEquals(List.of("NeoTech"),response.getScope().getAllowedBrands());
        assertFalse(response.getScope().getCanViewFullOrder());
        assertFalse(response.getScope().getCanManageUsers());
        assertEquals(List.of(),response.getDefaultFilters().getBrand());
    }

    @Test
    void shouldKeepEmptyBrandScopeForUnassignedAdmin() {
        User user = User.builder()
                .role(Role.ADMIN)
                .brand(null)
                .build();

        DashboardMetaResponse response = dashboardService
                .getDashboardMeta(user);

        assertEquals("BRAND",response.getScope().getMode());
        assertEquals(List.of(),response.getScope().getAllowedBrands());
        assertEquals(List.of(),response.getDefaultFilters().getBrand());
    }

    @Test
    void shouldClampDefaultStartTimeToMinimumDate() {
        businessProperties.setTime(LocalDate.of(2024,1,10));
        businessProperties.setStartTime(LocalDate.of(2024,1,1));

        DashboardMetaResponse response = dashboardService
                .getDashboardMeta(User.builder().role(Role.SUPER_ADMIN).build());

        assertEquals(LocalDate.of(2024,1,1),response
                .getDefaultDateRange().getStartTime());
        assertEquals(LocalDate.of(2024,1,10),response
                .getDefaultDateRange().getEndTime());
    }
}
