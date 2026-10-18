package com.product.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderPaymentStatsList {
    private String payWay;
    private String payWayLabel;
    private Long orderCount;
    private BigDecimal payRatio;
}
