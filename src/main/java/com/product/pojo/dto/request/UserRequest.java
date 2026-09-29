package com.product.pojo.dto.request;

import com.product.group.user.CheckGroup;
import com.product.group.user.CreateGroup;
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
public class UserRequest {
    @NotBlank(message = "验证密码不能为空",
            groups = {
                    CreateGroup.class
            }
    )
    private String confirmPassword;

    @NotBlank(message = "邮箱不能为空",
            groups = {
                    CreateGroup.class,
                    CheckGroup.class
            }
    )
    @Pattern(
            regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$",
            message = "邮箱格式不正确",
            groups = {
                    CreateGroup.class,
                    CheckGroup.class
            }
    )
    private String email;

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
    @NotBlank(
            groups = {CreateGroup.class},
            message = "用户昵称不能为空"
    )
    private String userName;

    private String city;

    private String gender;
}