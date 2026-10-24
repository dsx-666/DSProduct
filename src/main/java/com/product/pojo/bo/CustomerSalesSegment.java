package com.product.pojo.bo;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerSalesSegment {
    private String segment;
    private Long buyerCount;
    private Long orderCount;
    private BigDecimal salesAmount;
    private Long quantity;
    private List<ProductSalesRank> products;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductSalesRank {
        private Long rank;
        private Long productId;
        private String productName;
        private String brand;
        private String category;
        private BigDecimal salesAmount;
        private Long quantity;
        private Long buyerCount;
    }
}
