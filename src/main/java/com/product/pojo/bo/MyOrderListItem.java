package com.product.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyOrderListItem {
    private Long orderId;
    private BigDecimal amount;
    private BigDecimal originalAmount;
    private BigDecimal visibleAmount;
    private BigDecimal matchedAmount;
    private String contentScope;
    private String status;
    private LocalDateTime createTime;
    private Long userId;
    private String userName;
    private String payWay;
    private String productSummary;
    private Long productCount;
    private Boolean hasDetails;
    private Boolean hasAmountDifference;
}
