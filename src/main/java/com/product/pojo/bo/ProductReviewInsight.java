package com.product.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewInsight {
    private Long productId;
    private String productName;
    private String brand;
    private String category;
    private Long reviewCount;
    private BigDecimal averageRating;
    private Map<String,Long> ratingDistribution;
    private LocalDate latestReviewDate;
    private List<ReviewItem> reviews;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewItem {
        private Integer rating;
        private String reviewText;
        private java.time.LocalDateTime reviewDate;
    }
}
