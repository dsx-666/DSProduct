package com.product.pojo.bo;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GenderStatsGroup {
    private String gender;
    private String label;
    private Long buyerCount;
    private BigDecimal ratio;
    private BigDecimal salesAmount;
    private Long orderCount;
}
