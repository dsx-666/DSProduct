package com.product.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AccountStatus {
    ACTIVE("ACTIVE"),
    DISABLED("DISABLED");

    @EnumValue
    private final String status;
}
