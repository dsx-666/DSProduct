package com.product.pojo.bo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ProductRankingList {
    Long rank;
    BigDecimal salesAmount;
    Long salesVolume;
    Boolean isHot;
    ProductInfo product;
    @Data
    public static class ProductInfo{
        Long productId;
        String productName;
        String category;
        String categoryLabel;
        String brand;
    }
}
