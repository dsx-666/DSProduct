package com.product.controller;

import com.product.group.user.CheckGroup;
import com.product.group.user.CreateGroup;
import com.product.pojo.dto.request.UserRequest;
import com.product.pojo.dto.response.UserResponse;
import com.product.pojo.vo.RegisterUserVo;
import com.product.service.UserService;
import com.product.pojo.vo.LoginUserVo;
import com.product.pojo.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<RegisterUserVo> register(
            @RequestBody
            @Validated(CreateGroup.class)
            UserRequest userDto
    ) {
        UserResponse userResponse = userService.registerUser(userDto);


        return Result.success(
                RegisterUserVo.builder()
                        .user(
                            RegisterUserVo.User.builder()
                                    .email(userResponse.getEmail())
                                    .userName(userResponse.getUserName())
                                    .UserId(userResponse.getUserId())
                                    .brand(userResponse.getBrand())
                                    .CreateTime(userResponse.getCreateTime())
                                    .role(userResponse.getRole())
                                    .build()
                        )
                        .scope(userResponse.getScope())
                        .build()
        );
    }
//    @PostMapping("/login")
//    public Result<LoginUserVo> login(
//            @RequestBody
//            @Validated(CheckGroup.class)
//            UserRequest UserDto
//    ) {
//        return Result.success(userService.loginUser(UserDto));
//    }

}
