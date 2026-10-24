package com.product.pojo.dto.response;

import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AfterSaleStatsResponse {
    private Filter filters;
    private Scope scope;
    private Long totalOrderCount;
    private Long cancelledOrderCount;
    private Long returnedOrderCount;
    private BigDecimal cancelRate;
    private BigDecimal returnRate;
    private String status;
    private List<StatusCount> statusCounts;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private Long orderCount;
    }
}
