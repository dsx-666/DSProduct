package com.product.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepurchaseStatsGroup {
    private String key;
    private String label;
    private Long eligibleBuyerCount;
    private Long repurchaseBuyerCount;
    private BigDecimal repurchaseRate;
    private Long repeatOrderCount;
    private BigDecimal averageDaysBetweenOrders;
}
