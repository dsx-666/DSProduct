package com.product.pojo.dto.response;

import com.product.pojo.bo.OrderPaymentStatsList;
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
public class OrderPaymentStatsResponse {
    private Scope scope;
    private Long totalOrderCount;
    private List<OrderPaymentStatsList> payMethodList;
}
