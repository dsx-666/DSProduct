package com.product.service;

import com.product.pojo.dto.UserDto;
import com.product.pojo.vo.UserVo;
import com.product.pojo.vo.Result;


public interface UserService {
    UserVo registerUser(UserDto createUserDto);
    UserVo loginUser(UserDto updateUserDto);

}
