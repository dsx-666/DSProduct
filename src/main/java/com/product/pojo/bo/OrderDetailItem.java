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
public class OrderDetailItem {
    private Long orderItemId;
    private Long quantity;
    private BigDecimal itemPrice;
    private BigDecimal itemTotal;
    private ProductInfo product;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ProductInfo {
        private Long productId;
        private String productName;
        private String category;
        private String categoryLabel;
        private String brand;
        private BigDecimal currentPrice;
        private BigDecimal rating;
    }
}
