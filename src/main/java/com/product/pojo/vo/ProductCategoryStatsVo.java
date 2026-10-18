package com.product.pojo.vo;

import com.product.pojo.bo.ProductCategoryStatsList;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
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
public class ProductCategoryStatsVo {
    private Filter filters;
    private Scope scope;
    private BigDecimal totalSalesAmount;
    private List<ProductCategoryStatsList> categoryList;
}
