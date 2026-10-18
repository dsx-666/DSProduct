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
public class MyOrderListRow {
    private Long orderId;
    private String status;
    private LocalDateTime createTime;
    private Long userId;
    private String userName;
    private String payWay;
    private BigDecimal originalAmount;
    private BigDecimal itemsTotalAmount;
    private Long detailCount;
    private Long productCount;
    private String productSummary;
}
