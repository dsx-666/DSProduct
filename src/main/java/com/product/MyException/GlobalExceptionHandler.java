package com.product.MyException;

import com.product.enums.Code;
import com.product.pojo.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 获取参数校验的报错（为了让前端可以精确识别）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Map<String, String>> handleValidException(MethodArgumentNotValidException e) {
        // 收集所有字段错误，转成 Map
        Map<String, String> errors = e.getBindingResult()
                // 获取实例
                .getFieldErrors()
                //转化成流便于操作
                .stream()
                //最终结果对象转换.collect是调用对象转换，具体的实现方法在collectors
                .collect(Collectors.toMap(
                        FieldError::getField,           // key: 字段名
                        //FieldError::getDefaultMessage,   value: 错误提示
                        // 如果说返回一个null就是字符串"" lambada的用法
                        error -> Optional.ofNullable(error.getDefaultMessage()).orElse(""),
                        (v1, v2) -> v1                  // 如果同一字段有多个错误，取第一个
                ));
        return Result.error(Code.ParamError.getCode(),Code.ParamError.getDesc(), errors);
    }
    // 自定义报错（前端可以利用报错码来更好地处理不同的错误）
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(),e.getMessage(),null);
    }
    // 其他固定的异常处理
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error(e.getMessage());
        return Result.error(500, "系统繁忙，请稍后再试",null);
    }

}