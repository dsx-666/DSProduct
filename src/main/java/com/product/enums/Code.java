package com.product.enums;

import lombok.Getter;

@Getter
public enum Code{
    ParamError(20001,"参数错误"),
    ServerError(500,"服务器错误"),
    Success(200,"响应成功"),
    UniqueError(20002,"出现重复"),
    DataError(20003,"数据库错误"),
    SameError(20004,"不一致"),
    RegisterError(20005,"用户未注册"),
    LoginError(20006,"用户名或密码错误")

    ;

    private final Integer code;
    private final String desc;

    // 私有构造
    Code(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
