package com.product.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.product.MyException.BusinessException;
import com.product.config.BusinessProperties;
import com.product.pojo.other.JwtUtil;
import com.product.pojo.other.MyUserDetails;
import com.product.pojo.dto.UserDto;
import com.product.enums.Role;
import com.product.pojo.po.User;
import com.product.enums.Code;
import com.product.mapper.UserMapper;
import com.product.enums.UserType;
import com.product.security.token.PasswordAuthenticationToken;
import com.product.service.UserService;
import com.product.pojo.vo.UserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//仅给final 字段 + @NonNull 字段加入构造方法，可以用于构造器注入
@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager passwordAuthenticationManager;
    private final JwtUtil jwtUtil;
    private final BusinessProperties businessProperties;
    @Override
    // 事务同成功同失败
    @Transactional(rollbackFor = Exception.class)
    public UserVo registerUser(UserDto registerUserDto) {
        // 对验证密码进行校验
        if(!registerUserDto.getPassword().
                equals(registerUserDto.getConfirmPassword())) {
            throw new BusinessException(Code.SameError.getCode(),
                    "验证密码"+Code.SameError.getDesc());
        }
        // 对用户名称唯一性进行判断
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUserName, registerUserDto.getUserName());
        if(userMapper.exists(wrapper)) {
            throw new BusinessException(Code.UniqueError.getCode(),
                    "用户昵称"+ Code.UniqueError.getDesc());
        }
        // 创建user 实体类
        User user = User.builder()
                .userName(registerUserDto.getUserName())
                .password(passwordEncoder
                        .encode(registerUserDto.getPassword()))
                .gender(registerUserDto.getGender())
                .email(registerUserDto.getEmail())
                .city(registerUserDto.getCity())
                .role(Role.NORMAL_USER)
                .userType(UserType.NEW_USER)
                .signupDate(businessProperties.getTime())
                .build();
        // 插入数据库
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(Code.UniqueError.getCode(),
                    "用户昵称" + Code.UniqueError.getDesc());
        }
        // 返回给前端数据

        return UserVo.builder()
                .userName(user.getUserName())
                .city(user.getCity())
                .gender(user.getGender())
                .email(user.getEmail())
                .jwtToken(jwtUtil.generateToken(
                        user.getUserId(),
                        user.getUserName()
                ))
                .build();

    }
    // 利用AuthenticationManager来进行验证
    @Override
    public UserVo loginUser(UserDto loginUserDto) {

        PasswordAuthenticationToken token =
                new PasswordAuthenticationToken(
                        loginUserDto.getUserName(),
                        loginUserDto.getPassword()
                );
        // 捕获异常
        Authentication authentication;
        try {
            // 注意：try 的作用域不是整个函数，而是 try {} 这一对大括号形成的代码块。
            authentication = passwordAuthenticationManager.authenticate(token);

        } catch (UsernameNotFoundException e) {

            throw new BusinessException(Code.RegisterError.getCode(),
                    Code.RegisterError.getDesc()
            );

        } catch (BadCredentialsException e) {

            throw new BusinessException(Code.LoginError.getCode(),
                    Code.LoginError.getDesc()
            );

        } catch (Exception e) {

            throw new RuntimeException(e);

        }
        MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();
        return UserVo.builder()
                .userName(userDetails.getUsername())
                .jwtToken(jwtUtil.generateToken(userDetails.getUserId(),
                        userDetails.getUsername()))
                .build();

    }


}
