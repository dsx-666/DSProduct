package com.product.pojo.po;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.product.enums.Role;
import com.product.enums.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    @TableField("user_id")
    private Long userId;
    @TableField("name")
    private String userName;
    @TableField("password")
    private String password;
    @TableField("gender")
    private String gender;
    @TableField("city")
    private String city;
    @TableField("signup_date")
    private LocalDate signupDate;
    @TableField("user_type")
    private UserType userType;
    @TableField("role")
    private Role role;
    @TableField("email")
    private String email;
    @TableField("brand")
    private String brand;
}
