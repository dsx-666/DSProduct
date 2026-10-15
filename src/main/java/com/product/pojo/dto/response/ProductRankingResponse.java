package com.product.pojo.dto.response;

import com.product.pojo.bo.ProductRankingList;
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
public class ProductRankingResponse {
    Long soldProductCount;
    Long hotProductCount;
    List<ProductRankingList> rankList;
    Scope scope;
}
