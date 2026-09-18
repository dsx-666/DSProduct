package com.product.vo;

import com.product.enums.Code;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
// 返回前端统一的result
public class Result<T> {

    private int code;
    private String message;
    private T data;
    // 静态方法由于与jvm的生命周期相关等于说实体类没创建就有这个方法所以得声明T为泛型（<T>）
    public static <T> Result<T> success(T data) {
        return new Result<>(Code.Success.getCode(), Code.Success.getDesc(), data);
    }

    public static <T> Result<T> error(int code, String message,T data) {
        return new Result<>(code, message, data);
    }
}
