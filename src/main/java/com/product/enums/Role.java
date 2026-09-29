package com.product.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum Role {
    NORMAL_USER("NORMAL_USER","普通用户"),
    SUPER_ADMIN("SUPER_ADMIN","管理员"),
    ADMIN("ADMIN","商家");
    @EnumValue
    private final String role;
    private final String roleLabel;
    public static Role fromRoleLabel(String label) {
        for (Role category : values()) {
            if (category.roleLabel.equals(label)) {
                return category;
            }
        }
        throw new IllegalArgumentException("未知品类：" + label);
    }
    public static Role fromRole(String value) {
        for (Role category : values()) {
            if (category.role.equals(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("未知品类：" + value);
    }


}
