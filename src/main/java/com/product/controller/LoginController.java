package com.product.controller;

import com.product.dto.CreateUserDto;
import com.product.entity.User;
import com.product.mapper.UserMapper;
import com.product.service.CreateUserService;
import com.product.vo.CreateUserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.DriverManager;

@RestController
@RequestMapping("/user")
public class LoginController {

    @Autowired
    CreateUserService createUserService;

    @PostMapping("/login")
    public CreateUserVo login(CreateUserDto createUserDto) {

        return createUserService.createUser(createUserDto);




    }




}
