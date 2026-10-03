package com.product.pojo.dto.response;

import com.product.pojo.bo.QueryOrderListResult;
import com.product.pojo.vo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderListResponse {
    private List<QueryOrderListResult> orderList;
    private Long total;
    private Scope scope;
}
