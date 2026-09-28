package com.product.pojo.vo;

import com.product.pojo.other.QueryOrderListResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderListVo {
    private List<QueryOrderListResult> orderList;
    private Long total;
    private Long pageNum;
    private Long pageSize;
}
