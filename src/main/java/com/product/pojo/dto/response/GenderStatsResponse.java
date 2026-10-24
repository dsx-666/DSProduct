package com.product.pojo.dto.response;

import com.product.pojo.bo.Scope;
import com.product.pojo.bo.GenderStatsGroup;
import com.product.pojo.dto.request.Filter;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GenderStatsResponse {
    private Filter filters;
    private Scope scope;
    private Long totalBuyerCount;
    private List<GenderStatsGroup> groups;
}
