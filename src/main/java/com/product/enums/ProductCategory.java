package com.product.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/*
    设置返回json的参数
*/
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ProductCategory {
    Clothing("Clothing","服饰"),
    Groceries("Groceries","食品杂货"),
    Sports("Sports","运动用品"),
    Electronics("Electronics","电子产品"),
    HomeKitchen("Home & Kitchen","家居厨具"),
    Toys("Toys","玩具"),
    PetSupplies("Pet Supplies","宠物用品"),
    Books("Books","图书"),
    Automotive("Automotive","汽车用品"),
    Beauty("Beauty","美妆个护");
    @EnumValue
    private final String value;
    private final String label;
    // 通过一个属性寻找到一个枚举
    public static ProductCategory fromLabel(String label) {
        for (ProductCategory category : values()) {
            if (category.label.equals(label)) {
                return category;
            }
        }
        throw new IllegalArgumentException("未知品类：" + label);
    }
    public static ProductCategory fromValue(String value) {
        for (ProductCategory category : values()) {
            if (category.value.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("未知品类：" + value);
    }

}
