package com.product.pojo.dto.response;

import com.product.pojo.bo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductBrandResponse {
    private List<BrandOption> brands;
    private Scope scope;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BrandOption {
        private String value;
        private String label;
    }
}
