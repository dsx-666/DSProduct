package com.product.MyException;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiHttpException extends BusinessException {
    private final HttpStatus httpStatus;

    public ApiHttpException(HttpStatus httpStatus, int code, String message) {
        super(code, message);
        this.httpStatus = httpStatus;
    }
}
