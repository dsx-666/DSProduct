package com.product.controller;

import com.product.pojo.dto.UserDto;
import com.product.group.user.CheckGroup;
import com.product.group.user.CreateGroup;
import com.product.service.UserService;
import com.product.pojo.vo.UserVo;
import com.product.pojo.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<UserVo> register(
            @RequestBody
            @Validated(CreateGroup.class)
            UserDto userDto
    ) {
        return Result.success(userService.registerUser(userDto));
    }
    @PostMapping("/login")
    public Result<UserVo> login(
            @RequestBody
            @Validated(CheckGroup.class)
            UserDto UserDto
    ) {
        return Result.success(userService.loginUser(UserDto));
    }

}
