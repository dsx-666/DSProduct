package com.product.pojo.vo;

import com.product.pojo.po.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
//TODO:后续可以添加权限list，并且需要修改springSecurity权限相关的内容
public class Scope {
    private String mode;
    private List<String> allowedBrands;
    private Boolean canViewFullOrder;
    private Boolean canManageUsers;
}
