package com.product.pojo.dto.response;

import com.product.pojo.vo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderKpiResponse {
    /*
    * 整单差额绝对值超过 0.01 的订单数；ADMIN 为 null，表示无权核对
    * */
    private Long amountMismatchOrderCount;
    /*
    * 当前范围各订单 amount 的合计
    * */
    private BigDecimal totalSalesAmount;
    /*
    * 当前范围有效去重订单数
    * */
    private Long uniqueOrderCount;
    private BigDecimal averageOrderValue;
    /*
    * 入选订单中缺明细数量；ADMIN 恒为 0
    * */
    private Long missingDetailOrderCount;

    private Long soldProductCount;
    private Long hotProductCount;

    private Scope scope;


}
