package com.product.pojo.dto.response;

import com.product.pojo.bo.OrderDetailItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderDetailResponse {
    private Long orderId;
    private String status;
    private LocalDateTime createTime;
    private Long userId;
    private String userName;
    private String payWay;
    private String contentScope;
    private BigDecimal amount;
    private BigDecimal originalAmount;
    private BigDecimal itemsTotalAmount;
    private BigDecimal amountDifference;
    private Boolean hasAmountDifference;
    private BigDecimal visibleAmount;
    private Long visibleQuantity;
    private Long totalQuantity;
    private String amountSource;
    private Boolean hasDetails;
    private Long itemCount;
    private List<OrderDetailItem> items;
}
