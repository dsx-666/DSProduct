package com.product.pojo.vo;

import com.product.pojo.bo.Scope;
import com.product.pojo.dto.response.DashboardMetaResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DashboardMetaVo {
    private LocalDate businessDate;
    private String timeZone;
    private DashboardMetaResponse.DateRange dateRange;
    private DashboardMetaResponse.DefaultDateRange defaultDateRange;
    private DashboardMetaResponse.DefaultFilters defaultFilters;
    private List<DashboardMetaResponse.Option> orderStatusOptions;
    private List<DashboardMetaResponse.Option> rankingMetricOptions;
    private List<DashboardMetaResponse.Option> granularityOptions;
    private List<Long> quickRangeDays;
    private List<Long> pageSizeOptions;
    private Scope scope;
}
