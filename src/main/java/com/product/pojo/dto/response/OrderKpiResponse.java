package com.product.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderKpiResponse {
    private double totalSalesAmount;
    private Long uniqueOrderCount;
    private double averageOrderValue;
    private Long hotProductCount;
    private double hotProductAmount;
}
