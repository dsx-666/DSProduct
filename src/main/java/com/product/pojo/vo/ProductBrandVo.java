package com.product.pojo.vo;

import com.product.pojo.bo.Scope;
import com.product.pojo.dto.response.ProductBrandResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductBrandVo {
    private List<ProductBrandResponse.BrandOption> brands;
    private Scope scope;
}
