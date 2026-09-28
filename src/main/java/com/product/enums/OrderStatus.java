package com.product.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatus {
    processing("processing", "处理中"),
    completed("completed", "已完成"),
    cancelled("cancelled", "已取消"),
    shipped("shipped", "已发货"),
    returned("returned", "已拒绝");
    // EnumValue注解是用于存入数据库的数据字段  标记该字段存入数据库
    @EnumValue
    private final String desc;
    private final String name;
}