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
public class ProductCategoryStatsList {
    private String category;
    private String categoryName;
    private Boolean isUncategorized;
    private BigDecimal salesAmount;
    private Long salesVolume;
    private Long uniqueOrderCount;
    private BigDecimal salesRatio;
}
