package com.product.service;

import com.product.dto.CreateUserDto;
import com.product.vo.CreateUserVo;
import org.springframework.stereotype.Service;


public interface CreateUserService {

    CreateUserVo createUser(CreateUserDto createUserDto);

}
