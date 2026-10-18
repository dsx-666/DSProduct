package com.product.pojo.vo;

import com.product.pojo.bo.OrderPaymentStatsList;
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
public class OrderPaymentStatsVo {
    private Filter filters;
    private Scope scope;
    private Long totalOrderCount;
    private List<OrderPaymentStatsList> payMethodList;
}
