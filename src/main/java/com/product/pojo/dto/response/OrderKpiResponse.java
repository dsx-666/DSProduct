package com.product.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderKpiResponse {
    private BigDecimal totalSalesAmount;
    private Long uniqueOrderCount;
    private BigDecimal averageOrderValue;
    private Long hotProductCount;
    private BigDecimal hotProductAmount;
}
