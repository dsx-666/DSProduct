package com.product.pojo.vo;

import com.product.pojo.bo.Scope;
import com.product.pojo.dto.request.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderKpiVo {
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

    private Filter filters;

    private Scope scope;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderStatsVo {
        private Filter filters;
        private Scope scope;
        private String granularity;
        private List<LocalDate> dateList;
        private List<LocalDate> periodStartList;
        private List<LocalDate> periodEndList;
        private List<BigDecimal> salesAmountList;
        private List<Long> orderCountList;
    }

}
