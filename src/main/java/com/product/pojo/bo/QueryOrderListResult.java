package com.product.pojo.bo;
import com.product.pojo.vo.Scope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class QueryOrderListResult {
    private Long orderId;
    /*
     * 这个是以当前商品的价格为基础的订单总价加优惠卷那些的最后的价格，是最后的价格
     * 一般来说order表放的一个amount就是最后付款的价格
     * 所以这个就是total_amount
     * */
    private BigDecimal amount;
    private String status;
    private String userName;
    private String payWay;
    private LocalDateTime createTime;
    private Long userId;
    /*
    * 商品原价总和，还没扣整单优惠、改价、退款前的金额（注意这个必须得考虑商品价格可能会变化，
    * 所以才有了item表，表里面有在以前购买的商品的价格）
    * 由于这个没算折扣等一些内容所以肯定不是order表的amount，只可能是存放item里面的在当时的商品价格以及数量
    * 对其做乘法以及累加得到的
    *
    * */
    private BigDecimal originalPrice;
    /*
     * 这个是当前身份可以见到的金额
     * 如果说是平台管理员就是订单的全部商品的总金额
     * 如果说商家管理员就是在过滤好的基础上的总金额
     *
     * (think:
     *      如果说我进行一次查询（里面包括种类或关键字筛选）其中有一个订单既有筛选后的商品
     *      又有没有通过种类或者keyword筛选后的，但是符合商家可观察的品牌的商品
     *      visiblePrice是二者都加，而matchedAmount只加前者
     * )
     * */
    private BigDecimal visiblePrice;
    /*
     * 这个是筛选过后的总金额
     * */
    private BigDecimal matchedAmount;
    // 这个就是为了后续查看详细接口返回的内容
    private String contentScope;
    private String productSummary;
    private Long productCount;
    private Boolean hasDetail;
    private Boolean hasAmountDifference;

    public QueryOrderListResult ToList(
            Scope scope,
            BigDecimal originalAmount,
            BigDecimal visibleAmount) {
        // BigDecimal保留两位小数
        this.amount = this.amount.setScale(2, RoundingMode.HALF_UP);
        this.matchedAmount = this.matchedAmount.setScale(2, RoundingMode.HALF_UP);
        this.hasDetail = this.productCount>0;
        if (scope.getMode().equals("ALL")){
            this.contentScope = "FULL";
            this.originalPrice = originalAmount.setScale(2, RoundingMode.HALF_UP);
            this.visiblePrice = visibleAmount.setScale(2, RoundingMode.HALF_UP);
            // 这是BigDecimal的运算操作
            this.hasAmountDifference = this.originalPrice
                    .subtract(this.visiblePrice)
                    .abs()
                    .compareTo(new BigDecimal("0.01")) > 0;
        }else{
            this.contentScope = "BRAND";
            this.originalPrice = null;
            this.visiblePrice = visibleAmount.setScale(2, RoundingMode.HALF_UP);
            this.hasAmountDifference = null;
            this.amount = null;
        }
        String[] productName = this.productSummary.split("、");
        // 对productName进行处理
        if (productName.length > 2){
            this.productSummary = productName[0]+"、"+productName[1]+"等";
//            log.info(this.productSummary);
        }
        log.info(this.productSummary);
        return this;
    }

}
