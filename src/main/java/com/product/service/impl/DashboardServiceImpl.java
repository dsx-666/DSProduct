package com.product.service.impl;

import com.product.config.BusinessProperties;
import com.product.converter.UserConverter;
import com.product.pojo.dto.response.DashboardMetaResponse;
import com.product.pojo.po.User;
import com.product.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class DashboardServiceImpl implements DashboardService {
    private final BusinessProperties businessProperties;

    @Override
    public DashboardMetaResponse getDashboardMeta(User user) {
        LocalDate businessDate = businessProperties.getTime();
        LocalDate startTime = businessDate.minusDays(29);
        if(businessProperties.getStartTime() != null
                && startTime.isBefore(businessProperties.getStartTime())){
            startTime = businessProperties.getStartTime();
        }

        return DashboardMetaResponse.builder()
                .businessDate(businessDate)
                .timeZone("Asia/Shanghai")
                .dateRange(DashboardMetaResponse.DateRange.builder()
                        .minDate(businessProperties.getStartTime())
                        .maxDate(businessDate)
                        .build())
                .defaultDateRange(DashboardMetaResponse.DefaultDateRange.builder()
                        .startTime(startTime)
                        .endTime(businessDate)
                        .build())
                .defaultFilters(DashboardMetaResponse.DefaultFilters.builder()
                        .category(List.of())
                        .brand(List.of())
                        .statuses(List.of("completed"))
                        .keyWord("")
                        .metric("amount")
                        .granularity("day")
                        .page((long) 1)
                        .num((long) 20)
                        .build())
                .orderStatusOptions(List.of(
                        option("processing","处理中"),
                        option("shipped","已发货"),
                        option("completed","已完成"),
                        option("cancelled","已取消"),
                        option("returned","已退货")
                ))
                .rankingMetricOptions(List.of(
                        option("amount","销售额"),
                        option("quantity","销量")
                ))
                .granularityOptions(List.of(
                        option("day","按天"),
                        option("week","按周"),
                        option("month","按月")
                ))
                .quickRangeDays(List.of((long) 7,(long) 30,(long) 90))
                .pageSizeOptions(List.of((long) 10,(long) 20,(long) 50,(long) 100))
                .scope(UserConverter.toScope(user))
                .build();
    }

    private DashboardMetaResponse.Option option(String value,String label){
        return DashboardMetaResponse.Option.builder()
                .value(value)
                .label(label)
                .build();
    }
}
