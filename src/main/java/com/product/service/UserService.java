package com.product.service;

import com.product.pojo.dto.UserDto;
import com.product.pojo.vo.LoginUserVo;
import com.product.pojo.vo.RegisterUserVo;
import com.product.pojo.vo.Result;


public interface UserService {
    Result<RegisterUserVo> registerUser(UserDto createUserDto);
    Result<LoginUserVo> loginUser(UserDto updateUserDto);

}
