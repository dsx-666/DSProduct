package com.product.pojo.other;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueryOrderListResult {
    private Long orderId;
    private Double amount;
    private String status;
    private String userName;
    private String payMethod;
    private LocalDateTime createTime;
}
