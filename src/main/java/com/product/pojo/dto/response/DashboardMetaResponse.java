package com.product.pojo.dto.response;

import com.product.pojo.bo.Scope;
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
public class DashboardMetaResponse {
    private LocalDate businessDate;
    private String timeZone;
    private DateRange dateRange;
    private DefaultDateRange defaultDateRange;
    private DefaultFilters defaultFilters;
    private List<Option> orderStatusOptions;
    private List<Option> rankingMetricOptions;
    private List<Option> granularityOptions;
    private List<Long> quickRangeDays;
    private List<Long> pageSizeOptions;
    private Scope scope;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DateRange {
        private LocalDate minDate;
        private LocalDate maxDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DefaultDateRange {
        private LocalDate startTime;
        private LocalDate endTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DefaultFilters {
        private List<String> category;
        private List<String> brand;
        private List<String> statuses;
        private String keyWord;
        private String metric;
        private String granularity;
        private Long page;
        private Long num;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Option {
        private String value;
        private String label;
    }
}
