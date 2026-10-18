package com.product.controller;

import com.product.MyException.BusinessException;
import com.product.enums.Code;
import com.product.pojo.dto.response.DashboardMetaResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.DashboardMetaVo;
import com.product.pojo.vo.Result;
import com.product.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/meta")
    public Result<DashboardMetaVo> getDashboardMeta(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            HttpServletRequest request) {
        if(!request.getParameterMap().isEmpty()){
            throw new BusinessException(Code.ParamError.getCode(),
                    Code.ParamError.getDesc()+":存在不支持的参数");
        }

        DashboardMetaResponse dashboardMetaResponse = dashboardService
                .getDashboardMeta(myUserDetails.getUser());

        return Result.success(
                DashboardMetaVo.builder()
                        .businessDate(dashboardMetaResponse.getBusinessDate())
                        .timeZone(dashboardMetaResponse.getTimeZone())
                        .dateRange(dashboardMetaResponse.getDateRange())
                        .defaultDateRange(dashboardMetaResponse.getDefaultDateRange())
                        .defaultFilters(dashboardMetaResponse.getDefaultFilters())
                        .orderStatusOptions(dashboardMetaResponse
                                .getOrderStatusOptions())
                        .rankingMetricOptions(dashboardMetaResponse
                                .getRankingMetricOptions())
                        .granularityOptions(dashboardMetaResponse
                                .getGranularityOptions())
                        .quickRangeDays(dashboardMetaResponse.getQuickRangeDays())
                        .pageSizeOptions(dashboardMetaResponse.getPageSizeOptions())
                        .scope(dashboardMetaResponse.getScope())
                        .build()
        );
    }
}
