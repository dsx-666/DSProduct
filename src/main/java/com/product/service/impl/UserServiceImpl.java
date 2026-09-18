package com.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.product.MyException.BusinessException;
import com.product.dto.CreateUserDto;
import com.product.entity.User;
import com.product.enums.Code;
import com.product.mapper.UserMapper;
import com.product.service.UserService;
import com.product.vo.CreateUserVo;
import com.product.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Result<CreateUserVo> createUser(CreateUserDto createUserDto) {
        User user = User.builder().
                name(createUserDto.getName()).
                password(createUserDto.getPassword()).
                build();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getName, createUserDto.getName());
        if(userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(Code.UniqueError.getCode(),
                    "用户昵称"+ Code.UniqueError.getDesc());
        }
        try {
            userMapper.insert(user);
        }
        catch(Exception e) {
            throw new BusinessException(Code.DataError.getCode(), "用户注册失败");
        }
        CreateUserVo createUserVo = CreateUserVo.builder()
                .username(createUserDto.getName())
                .build();

        return new Result<CreateUserVo>(Code.Success.getCode(),
                Code.Success.getDesc(),
                createUserVo);

    }


}
