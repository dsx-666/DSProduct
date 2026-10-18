package com.product.pojo.dto.response;

import com.product.pojo.bo.ProductCategoryStatsList;
import com.product.pojo.bo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductCategoryStatsResponse {
    private BigDecimal totalSalesAmount;
    private List<ProductCategoryStatsList> categoryList;
    private Scope scope;
}
