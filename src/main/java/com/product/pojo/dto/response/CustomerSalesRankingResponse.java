package com.product.pojo.dto.response;

import com.product.pojo.bo.CustomerSalesSegment;
import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerSalesRankingResponse {
    private Filter filters;
    private Scope scope;
    private String metric;
    private List<String> segments;
    private List<CustomerSalesSegment> data;
}
