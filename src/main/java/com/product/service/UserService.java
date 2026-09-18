package com.product.service;

import com.product.dto.CreateUserDto;
import com.product.mapper.UserMapper;
import com.product.vo.CreateUserVo;
import com.product.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


public interface UserService {


    Result<CreateUserVo> createUser(CreateUserDto createUserDto);

}
