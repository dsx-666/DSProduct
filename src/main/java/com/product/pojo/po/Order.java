package com.product.pojo.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("order")
public class Order {
    @TableField("order_id")
    private int orderId;
    @TableField("user_id")
    private int userId;
    @TableField("order_date")
    private LocalDateTime orderDate;
    @TableField("order_status")
    private String orderStatus;
    @TableField("total_amount")
    private double totalAmount;
    @TableField("pay_way")
    private String payMethod;

}
