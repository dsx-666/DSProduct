package com.product.pojo.dto.response;

import com.product.pojo.bo.RepurchaseStatsGroup;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepurchaseStatsResponse {
    private Filter filters;
    private Scope scope;
    private Long eligibleBuyerCount;
    private Long repurchaseBuyerCount;
    private BigDecimal repurchaseRate;
    private Long repeatOrderCount;
    private BigDecimal averageDaysBetweenOrders;
    private String status;
    private String groupBy;
    private List<RepurchaseStatsGroup> groups;
}
