package com.product.pojo.vo;

import com.product.pojo.bo.ProductRankingList;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductRankingVo {
    Filter filter;
    Scope scope;
    String metric;
    Long limit;
    Long soldProductCount;
    Long hotProductCount;
    List<ProductRankingList> rankList;
}
