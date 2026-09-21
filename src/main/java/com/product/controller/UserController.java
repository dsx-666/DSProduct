package com.product.controller;

import com.product.pojo.dto.UserDto;
import com.product.group.CheckGroup;
import com.product.group.CreateGroup;
import com.product.service.UserService;
import com.product.pojo.vo.LoginUserVo;
import com.product.pojo.vo.RegisterUserVo;
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
    public Result<RegisterUserVo> register(
            @RequestBody
            @Validated(CreateGroup.class)
            UserDto userDto
    ) {
        return userService.registerUser(userDto);

    }
    @GetMapping("/login")
    public Result<LoginUserVo> login(
            @RequestBody
            @Validated(CheckGroup.class)
            UserDto UserDto
    ) {
        return userService.loginUser(UserDto);
    }

}
