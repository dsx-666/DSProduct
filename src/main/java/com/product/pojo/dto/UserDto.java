package com.product.pojo.dto;

import com.product.group.CheckGroup;
import com.product.group.CreateGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
//TODO: 后期可能会修改，因为业务可能会重复（增加分组校验）
public class UserDto {
    @NotBlank(message = "用户名不能为空",
            groups = {
                CreateGroup.class,
                CheckGroup.class
            }
    )
    @Pattern(
            regexp = "^[a-zA-Z][a-zA-Z0-9_]{5,31}$",
            message = "用户名必须以字母开头，长度6-31位，只能包含字母、数字和下划线",
            groups = {
                    CreateGroup.class,
                    CheckGroup.class
            }
    )
    private String username;

    @NotBlank(message = "密码不能为空",
            groups = {
                    CreateGroup.class,
                    CheckGroup.class
            }
    )
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,20}$",
            message = "密码长度6-20位，必须包含大写字母、小写字母和数字",
            groups = {
                    CreateGroup.class,
            }
    )
    private String password;

    @NotBlank(message = "验证密码不能为空",
            groups = {
                    CreateGroup.class
            }
    )
    private String confirmPassword;
}