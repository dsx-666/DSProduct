package com.product.pojo.dto.response;

import com.product.pojo.bo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCustomerStatsResponse {
    private Scope scope;
    private LocalDate businessDate;
    private Long newUserDays;
    private Long totalPurchaseUserCount;
    private Long newUserCount;
    private Long oldUserCount;
    private Long validPurchaseUserCount;
    private BigDecimal newUserRatio;
    private BigDecimal oldUserRatio;
    private Long abnormalUserCount;
    private BigDecimal abnormalUserRatio;
}
