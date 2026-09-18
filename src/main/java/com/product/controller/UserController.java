package com.product.controller;

import com.product.dto.CreateUserDto;
import com.product.service.UserService;
import com.product.vo.CreateUserVo;
import com.product.vo.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<CreateUserVo> register(@RequestBody @Valid CreateUserDto createUserDto) {
        return userService.createUser(createUserDto);
    }




}
