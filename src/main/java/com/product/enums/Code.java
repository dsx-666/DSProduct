package com.product.enums;

import lombok.Getter;

@Getter
public enum Code{
    Null(6001,"该字段为空"),
    StyleError(6002,"格式错误"),
    ServerError(6003,"服务器错误"),
    Success(200,"响应成功")
    ;




    private final Integer code;
    private final String desc;

    // 私有构造
    Code(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
