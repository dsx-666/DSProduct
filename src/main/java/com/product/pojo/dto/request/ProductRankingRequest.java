package com.product.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductRankingRequest {
    // 需要不为null才可以注入，因为是通过set注入
    @JsonUnwrapped
    Filter filter = new Filter();
    String metric;
}
