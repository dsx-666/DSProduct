package com.product.dto;

import lombok.*;
import jakarta.validation.constraints.*;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
//TODO: 后期可能会修改，因为业务可能会重复（增加分组校验）
public class CreateUserDto {
    @NotBlank(message = "用户名不能为空")
    @Pattern(
            regexp = "^[a-zA-Z][a-zA-Z0-9_]{5,31}$",
            message = "用户名必须以字母开头，长度6-31位，只能包含字母、数字和下划线"
    )
    private String name;

    @NotBlank(message = "密码不能为空")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,20}$",
            message = "密码长度6-20位，必须包含大写字母、小写字母和数字"
    )
    private String password;
}
