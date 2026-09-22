package com.product.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.product.MyException.BusinessException;
import com.product.pojo.JwtUtil;
import com.product.pojo.MyUserDetails;
import com.product.pojo.dto.UserDto;
import com.product.pojo.entity.User;
import com.product.enums.Code;
import com.product.mapper.UserMapper;
import com.product.security.token.PasswordAuthenticationToken;
import com.product.service.UserService;
import com.product.pojo.vo.LoginUserVo;
import com.product.pojo.vo.RegisterUserVo;
import com.product.pojo.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    @Override
    // 事务同成功同失败
    @Transactional(rollbackFor = Exception.class)
    public Result<RegisterUserVo> registerUser(UserDto registerUserDto) {
        // 对验证密码进行校验
        if(!registerUserDto.getPassword().
                equals(registerUserDto.getConfirmPassword())) {
            throw new BusinessException(Code.SameError.getCode(),
                    "验证密码"+Code.SameError.getDesc());
        }
        // 对用户名称唯一性进行判断
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, registerUserDto.getUsername());
        if(userMapper.exists(wrapper)) {
            throw new BusinessException(Code.UniqueError.getCode(),
                    "用户昵称"+ Code.UniqueError.getDesc());
        }
        // 创建user 实体类
        User user = User.builder().
                username(registerUserDto.getUsername()).
                password(passwordEncoder.
                        encode(registerUserDto.getPassword())).
                build();
        // 插入数据库
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(Code.UniqueError.getCode(),
                    "用户昵称" + Code.UniqueError.getDesc());
        }
        // 返回给前端数据
        RegisterUserVo registerUserVo = RegisterUserVo.builder()
                .username(registerUserDto.getUsername())
                .build();

        return new Result<>(Code.Success.getCode(),
                Code.Success.getDesc(),
                registerUserVo);

    }
    // 利用AuthenticationManager来进行验证
    @Override
    public Result<LoginUserVo> loginUser(UserDto loginUserDto) {

        PasswordAuthenticationToken token =
                new PasswordAuthenticationToken(
                        loginUserDto.getUsername(),
                        loginUserDto.getPassword()
                );
        // 捕获异常
        Authentication authentication;
        try {
            // 注意：try 的作用域不是整个函数，而是 try {} 这一对大括号形成的代码块。
            authentication = authenticationManager.authenticate(token);

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
        LoginUserVo loginUserVo = LoginUserVo.builder()
                .username(userDetails.getUsername())
                .jwtToken(jwtUtil.generateToken(userDetails.getUserId(),
                        userDetails.getUsername()))
                .build();
        return new Result<>(Code.Success.getCode(),
                Code.Success.getDesc(),
                loginUserVo);

    }


}
