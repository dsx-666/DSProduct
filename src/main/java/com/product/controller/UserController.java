package com.product.controller;

import com.product.group.user.CheckGroup;
import com.product.group.user.CreateGroup;
import com.product.pojo.dto.request.UserRequest;
import com.product.pojo.dto.response.UserResponse;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.RegisterUserVo;
import com.product.service.JwtBlacklistService;
import com.product.service.UserService;
import com.product.pojo.vo.LoginUserVo;
import com.product.pojo.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final JwtBlacklistService jwtBlacklistService;

    @PostMapping("/register")
    public Result<RegisterUserVo> register(
            @RequestBody
            @Validated(CreateGroup.class)
            UserRequest userRequest
    ) {
        UserResponse userResponse = userService.registerUser(userRequest);


        return Result.success(
                RegisterUserVo.builder()
                        .user(
                            RegisterUserVo.TempUser.builder()
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
    @PostMapping("/login")
    public Result<LoginUserVo> login(
            @RequestBody
            @Validated(CheckGroup.class)
            UserRequest userRequest
    ) {
        UserResponse userResponse = userService.loginUser(userRequest);
        return Result.success(
                LoginUserVo.builder()
                        .JwtToken(userResponse.getJwtToken())
                        .scope(userResponse.getScope())
                        .user(
                                LoginUserVo.TempUser.builder()
                                        .UserId(userResponse.getUserId())
                                        .email(userResponse.getEmail())
                                        .userName(userResponse.getUserName())
                                        .role(userResponse.getRole())
                                        .brand(userResponse.getBrand())
                                        .build()
                        )
                        .build()
        );
    }
    @GetMapping("/me")
    public Result<LoginUserVo> Me(
            @AuthenticationPrincipal MyUserDetails myUserDetails,
            @RequestHeader("Authorization") String auth
    ) {
        String token = auth.replaceFirst("^Bearer\\s+", "");
        return Result.success(
                LoginUserVo.builder()
                        .user(
                                LoginUserVo.TempUser.builder()
                                        .userName(myUserDetails.getUser().getUserName())
                                        .email(myUserDetails.getUser().getEmail())
                                        .UserId(myUserDetails.getUser().getUserId())
                                        .brand(myUserDetails.getUser().getBrand())
                                        .role(myUserDetails.getUser().getRole())
                                        .CreateTime(myUserDetails.getUser().getSignupDate())
                                        .build()
                        )
                        .JwtToken(token)
                        .scope(myUserDetails.getScope())
                        .build()
        );
    }
    @PostMapping("/logout")
    public Result<Void> Logout(
            @RequestHeader("Authorization") String auth
    ) {
        String token = auth.substring(7);
        jwtBlacklistService.addBlacklist(token);
        return Result.success(null);
    }

}
