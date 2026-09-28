package com.product.pojo.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("product")
public class Product {
    @TableField("product_id")
    private int productId;
    @TableField("product_name")
    private String productName;
    @TableField("category")
    private String category;
    @TableField("brand")
    private String brand;
    @TableField("price")
    private double price;
    @TableField("rating")
    private double rating;
}
